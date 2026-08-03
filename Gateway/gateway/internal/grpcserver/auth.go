// gRPC 网关 JWT 鉴权：从 metadata 提取 Bearer Token，复用 HTTP 网关的 auth/tenant 逻辑。
// 透明代理走 StreamHandler，须用 StreamServerInterceptor（Unary 拦截器不会执行）。
package grpcserver

import (
	"context"
	"strings"

	"gateway/internal/auth"
	"gateway/internal/tenant"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
)

type ctxKey int

const (
	TargetKey ctxKey = 1
	tenantKey ctxKey = 2
)

// AuthStreamInterceptor 透明代理场景下的 JWT 鉴权（grpc-proxy 使用 StreamHandler）。
func AuthStreamInterceptor(secret []byte, required bool) grpc.StreamServerInterceptor {
	return func(srv any, ss grpc.ServerStream, info *grpc.StreamServerInfo, handler grpc.StreamHandler) error {
		newCtx, err := authenticate(ss.Context(), secret, required)
		if err != nil {
			return err
		}
		return handler(srv, &wrappedServerStream{ServerStream: ss, ctx: newCtx})
	}
}

// authenticate 从 incoming metadata 校验 JWT，成功后将 tenant 写入 context。
func authenticate(ctx context.Context, secret []byte, required bool) (context.Context, error) {
	md, ok := metadata.FromIncomingContext(ctx)
	if !ok || len(md.Get("authorization")) == 0 {
		if required {
			return ctx, status.Error(codes.Unauthenticated, "missing token")
		}
		return ctx, nil
	}
	raw := md.Get("authorization")[0]
	tokenStr, ok := parseBearer(raw)
	if !ok {
		return ctx, status.Error(codes.Unauthenticated, "invalid authorization format")
	}
	claims, err := auth.Parse(tokenStr, secret)
	if err != nil {
		return ctx, status.Error(codes.Unauthenticated, "invalid token")
	}
	if !tenant.IsValid(claims.Tenant) {
		return ctx, status.Error(codes.Unauthenticated, "unknown tenant")
	}
	ctx = context.WithValue(ctx, tenantKey, claims.Tenant)
	ctx = metadata.AppendToOutgoingContext(ctx, "x-tenant", claims.Tenant)
	return ctx, nil
}

func parseBearer(v string) (string, bool) {
	const p = "Bearer "
	if !strings.HasPrefix(v, p) {
		return "", false
	}
	return strings.TrimSpace(v[len(p):]), true
}

type wrappedServerStream struct {
	grpc.ServerStream
	ctx context.Context
}

func (w *wrappedServerStream) Context() context.Context {
	return w.ctx
}

func tenantFromContext(ctx context.Context) string {
	if t, ok := ctx.Value(tenantKey).(string); ok && t != "" {
		return t
	}
	md, ok := metadata.FromOutgoingContext(ctx)
	if !ok {
		return ""
	}
	vals := md.Get("x-tenant")
	if len(vals) == 0 {
		return ""
	}
	return vals[0]
}
