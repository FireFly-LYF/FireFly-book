// 兼容入口：仅启动 gRPC。推荐改用 cmd/gateway（HTTP + gRPC 单进程）。
package main

import (
	"context"
	"flag"
	"log"
	"strings"
	"time"

	"gateway/internal/config"
	"gateway/internal/proxy"
	"gateway/internal/ratelimit"
	redisx "gateway/internal/redis"
	"gateway/internal/registry"

	"github.com/redis/go-redis/v9"
)

var configPath = flag.String("config", "internal/config/gateway.yaml", "path to gateway config file")

func main() {
	flag.Parse()
	log.Print("提示：推荐 go run ./cmd/gateway 单进程同时提供 HTTP 与 gRPC")

	cfg, err := config.Load(*configPath)
	if err != nil {
		log.Fatal(err)
	}
	if !cfg.GRPCEnabled() {
		log.Fatal("grpc.listen 未配置，请在 gateway.yaml 中设置 grpc.listen")
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
	reg := registry.NewMemory(cfg.Upstreams())
	bs, err := registry.NewBalancers(lbCfg)
	if err != nil {
		log.Fatal(err)
	}
	registry.Sync(reg, bs)
	registry.StartHealthCheck(reg, bs, 10*time.Second)

	grpcListen := cfg.GRPCListen()
	grpcUpstreams := strings.Join(reg.ListHealthyGRPCAddrs(), ", ")
	log.Printf("grpc gateway listening on %s, auth_required=%v, lb=%s, upstreams=[%s]",
		grpcListen, cfg.GRPCAuthRequired(), lbCfg.Strategy, grpcUpstreams)

	if err := proxy.Run(grpcListen, cfg.JWTSecret(), cfg.GRPCAuthRequired(), limiter, breaker, recorder, bs.GRPC); err != nil {
		log.Fatal(err)
	}
}
