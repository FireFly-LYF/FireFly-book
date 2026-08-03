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
	"gateway/internal/proxy"
	"gateway/internal/middleware"
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

	// ① 加载 yaml：租户白名单、JWT、LB 策略、HTTP/gRPC 下游等
	cfg, err := config.Load(*configPath)
	if err != nil {
		log.Fatal(err)
	}

	// ② 连接 Redis；连不上则 client=nil，限流降级为本地滑动窗口，统计跳过
	redisAddr := cfg.RedisAddr()
	candidate := redisx.New(redisAddr)
	var client *redis.Client
	if err := redisx.Ping(context.Background(), candidate); err != nil {
		log.Printf("redis unavailable (%s): %v, ratelimit fallback to local sliding window", redisAddr, err)
	} else {
		client = candidate
	}

	// ③ 网关治理组件（HTTP / gRPC 共用同一实例，配额与熔断不区分协议）
	limiter := ratelimit.NewLimiter(client, cfg.RateLimitRate(), cfg.RateLimitCapacity(), cfg.RateLimitDailyLimit())
	var breaker *registry.CircuitBreaker
	if cfg.CircuitBreakerEnabled() {
		breaker = registry.NewCircuitBreaker(cfg.CBThreshold(), cfg.CBCooldown())
	}
	recorder := redisx.NewStatsRecorder(client)

	// ④ 统一下游注册表 + 三个 balancer 视图（策略相同，节点按 HTTP/gRPC/TCP 过滤）
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
	registry.Sync(reg, bs)                                     // 健康节点 → SetNodes
	registry.StartHealthCheck(reg, bs, 10*time.Second)         // 后台每 10s 探测并刷新

	// ⑤ gRPC 透明代理（goroutine 并行监听 grpc.listen，与 HTTP 共用 limiter/breaker/recorder）
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

	// ⑤b TCP 透明代理（连接级转发，复用 LB / 熔断 / IP 黑名单）
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

	// ⑥ 注册 HTTP 路由：/gateway/* 管理面，/api/* 业务链（JWT → 限流 → 统计 → 代理）
	r := gin.New()
	handler.New(cfg, client, limiter, breaker, recorder, reg, bs).Register(r)

	ratelimitMode := "redis token-bucket"
	if client == nil {
		ratelimitMode = "local sliding window (redis down)"
	}
	httpUpstreams := strings.Join(reg.ListHealthyHTTPURLs(), ", ")
	grpcUpstreams := strings.Join(reg.ListHealthyGRPCAddrs(), ", ")
	tcpUpstreams := strings.Join(reg.ListHealthyTCPAddrs(), ", ")
	log.Printf("http gateway listening on %s, redis=%s, ratelimit=%s qps=%d/%d qpd=%d, lb=%s, http_upstreams=[%s], grpc_upstreams=[%s], tcp_upstreams=[%s]",
		cfg.Server.Addr, redisAddr, ratelimitMode, cfg.RateLimitRate(), cfg.RateLimitCapacity(), cfg.RateLimitDailyLimit(), lbCfg.Strategy, httpUpstreams, grpcUpstreams, tcpUpstreams)

	// ⑦ 阻塞监听 server.addr（主 goroutine；gRPC 已在 ⑤ 中启动）
	if err := r.Run(cfg.Server.Addr); err != nil {
		log.Fatal(err)
	}
}
