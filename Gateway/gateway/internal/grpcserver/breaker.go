package grpcserver

import (
	"context"

	"gateway/internal/registry"

	"github.com/mwitkow/grpc-proxy/proxy"
	"google.golang.org/grpc"
)

// TransparentHandler 包装 grpc-proxy，在 RPC 结束后按 director 选中的节点更新熔断。
func TransparentHandler(director proxy.StreamDirector, cb *registry.CircuitBreaker) grpc.StreamHandler {
	if cb == nil {
		return proxy.TransparentHandler(director)
	}
	return func(srv any, ss grpc.ServerStream) error {
		var target string
		d := func(ctx context.Context, method string) (context.Context, grpc.ClientConnInterface, error) {
			newCtx, conn, err := director(ctx, method)
			if t, ok := newCtx.Value(TargetKey).(string); ok {
				target = t
			}
			return newCtx, conn, err
		}
		err := proxy.TransparentHandler(d)(srv, ss)
		if target != "" {
			cb.Record(target, err != nil)
		}
		return err
	}
}
