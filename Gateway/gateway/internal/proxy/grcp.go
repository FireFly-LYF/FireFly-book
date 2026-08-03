package proxy

import (
	"context"
	"log"
	"net"

	"gateway/internal/grpcserver"
	"gateway/internal/lb"
	"gateway/internal/ratelimit"
	"gateway/internal/registry"
	redisx "gateway/internal/redis"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/peer"
	"google.golang.org/grpc/status"
)

// Run 启动 gRPC 透明代理网关。
// grpc-proxy 走 StreamHandler，治理逻辑须用 StreamServerInterceptor + 包装 TransparentHandler。
func Run(listen string, secret []byte, authRequired bool, limiter *ratelimit.Limiter, cb *registry.CircuitBreaker, rec *redisx.StatsRecorder, balancer lb.Balancer) error {
	pool := grpcserver.NewPool()
	defer pool.Close()

	director := func(ctx context.Context, fullMethodName string) (context.Context, grpc.ClientConnInterface, error) {
		key := clientIP(ctx)
		if key == "" {
			key = fullMethodName
		}
		target, ok := balancer.NextKey(key)
		if !ok {
			return ctx, nil, status.Error(codes.Unavailable, "no grpc upstream available")
		}
		if cb != nil && !cb.Allow(target) {
			return ctx, nil, status.Error(codes.Unavailable, "circuit open")
		}
		log.Printf("[grpc director] %s -> %s", fullMethodName, target)
		conn, err := pool.Get(target)
		if err != nil {
			if cb != nil {
				cb.Record(target, true)
			}
			return ctx, nil, err
		}
		ctx = context.WithValue(ctx, grpcserver.TargetKey, target)
		return ctx, conn, nil
	}

	streamInterceptors := []grpc.StreamServerInterceptor{
		grpcserver.AuthStreamInterceptor(secret, authRequired),
	}
	if limiter != nil {
		streamInterceptors = append(streamInterceptors, grpcserver.RateLimitStreamInterceptor(limiter))
	}
	if rec != nil {
		streamInterceptors = append(streamInterceptors, grpcserver.StatsStreamInterceptor(rec))
	}

	s := grpc.NewServer(
		grpc.ChainStreamInterceptor(streamInterceptors...),
		grpc.UnknownServiceHandler(grpcserver.TransparentHandler(director, cb)),
	)

	lis, err := net.Listen("tcp", listen)
	if err != nil {
		return err
	}
	return s.Serve(lis)
}

func clientIP(ctx context.Context) string {
	p, ok := peer.FromContext(ctx)
	if !ok || p.Addr == nil {
		return ""
	}
	//从 gRPC 请求的 context 里取出客户端 IP
	host, _, err := net.SplitHostPort(p.Addr.String())
	if err != nil {
		return p.Addr.String()
	}
	return host
}
