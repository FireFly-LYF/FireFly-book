// HTTP + gRPC + TCP 三入口网关
//
// 单进程同时监听 server.addr（HTTP）、grpc.listen（gRPC）、tcp.listen（TCP 透明代理）。
// 启动：在 gateway 目录下 go run ./cmd/gateway
package main

import (
	"context"
	"flag"
	"log"
	"strings"
	"time"

	"gateway/internal/config"
	"gateway/internal/handler"
	"gateway/internal/middleware"
	"gateway/internal/proxy"
	"gateway/internal/ratelimit"
	redisx "gateway/internal/redis"
	"gateway/internal/registry"

	"github.com/gin-gonic/gin"
	"github.com/redis/go-redis/v9"
)

// 默认配置文件路径；在 gateway 目录下启动时相对当前工作目录。
var configPath = flag.String("config", "internal/config/gateway.yaml", "path to gateway config file")

func main() {
	flag.Parse()

	cfg, err := config.Load(*configPath)
	if err != nil {
		log.Fatal(err)
	}

	redisAddr := cfg.RedisAddr()
	candidate := redisx.New(redisAddr)
	var client *redis.Client
	if err := redisx.Ping(context.Background(), candidate); err != nil {
		log.Printf("redis unavailable (%s): %v, ratelimit fallback to local sliding window", redisAddr, err)
	} else {
		client = candidate
	}

	limiter := ratelimit.NewLimiter(client, cfg.RateLimitRate(), cfg.RateLimitCapacity(), cfg.RateLimitDailyLimit())
	var breaker *registry.CircuitBreaker
	if cfg.CircuitBreakerEnabled() {
		breaker = registry.NewCircuitBreaker(cfg.CBThreshold(), cfg.CBCooldown())
	}
	recorder := redisx.NewStatsRecorder(client)

	lbCfg := cfg.LBConfig()
	var reg registry.Registry
	switch cfg.RegistryType() {
	case "mysql":
		reg, err = registry.NewMySQL(cfg.RegistryDSN(), cfg.Upstreams())
		if err != nil {
			log.Fatalf("mysql registry: %v", err)
		}
		log.Printf("registry=mysql, nodes=%d", len(reg.List()))
	default:
		reg = registry.NewMemory(cfg.Upstreams())
		log.Printf("registry=memory, nodes=%d", len(reg.List()))
	}
	bs, err := registry.NewBalancers(lbCfg)
	if err != nil {
		log.Fatal(err)
	}
	registry.Sync(reg, bs)
	registry.StartHealthCheck(reg, bs, 10*time.Second)

	var httpRouter *registry.HTTPRouter
	if cfg.HasHTTPRoutes() {
		httpRouter = registry.NewHTTPRouter()
		for _, rc := range cfg.Routes {
			routeReg := registry.NewMemory(cfg.RouteUpstreams(rc))
			routeBS, err := registry.NewBalancers(lbCfg)
			if err != nil {
				log.Fatalf("route %s balancer: %v", rc.ID, err)
			}
			registry.Sync(routeReg, routeBS)
			registry.StartHealthCheck(routeReg, routeBS, 10*time.Second)
			if err := httpRouter.Add(&registry.HTTPRoute{
				ID:           rc.ID,
				Prefix:       rc.Prefix,
				AuthRequired: cfg.RouteAuthRequired(rc),
				Reg:          routeReg,
				Balancers:    routeBS,
			}); err != nil {
				log.Fatalf("route %s: %v", rc.ID, err)
			}
			log.Printf("http route id=%s prefix=%s upstreams=%v", rc.ID, rc.Prefix, routeReg.ListHealthyHTTPURLs())
		}
	}

	if cfg.GRPCEnabled() {
		grpcListen := cfg.GRPCListen()
		grpcUpstreams := strings.Join(reg.ListHealthyGRPCAddrs(), ", ")
		go func() {
			log.Printf("grpc gateway listening on %s, auth_required=%v, lb=%s, upstreams=[%s]",
				grpcListen, cfg.GRPCAuthRequired(), lbCfg.Strategy, grpcUpstreams)
			if err := proxy.Run(grpcListen, cfg.JWTSecret(), cfg.GRPCAuthRequired(), limiter, breaker, recorder, bs.GRPC); err != nil {
				log.Fatalf("grpc serve: %v", err)
			}
		}()
	}

	ipBlockSet := middleware.IPsToSet(cfg.Security.IPBlocklist)
	if cfg.TCPEnabled() {
		tcpListen := cfg.TCPListen()
		tcpUpstreams := strings.Join(reg.ListHealthyTCPAddrs(), ", ")
		go func() {
			log.Printf("tcp gateway listening on %s, lb=%s, upstreams=[%s]",
				tcpListen, lbCfg.Strategy, tcpUpstreams)
			if err := proxy.RunTCP(tcpListen, bs.TCP, breaker, ipBlockSet); err != nil {
				log.Fatalf("tcp serve: %v", err)
			}
		}()
	}

	r := gin.New()
	handler.New(cfg, client, limiter, breaker, recorder, reg, bs, httpRouter).Register(r)

	ratelimitMode := "redis token-bucket"
	if client == nil {
		ratelimitMode = "local sliding window (redis down)"
	}
	mode := "single-pool"
	if httpRouter != nil {
		mode = "path-routes"
	}
	log.Printf("http gateway listening on %s, mode=%s, strip_prefix=%q, api_auth=%v, redis=%s, ratelimit=%s",
		cfg.Server.Addr, mode, cfg.StripPrefix(), cfg.APIAuthRequired(), redisAddr, ratelimitMode)

	if err := r.Run(cfg.Server.Addr); err != nil {
		log.Fatal(err)
	}
}
