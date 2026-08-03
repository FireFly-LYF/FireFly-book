package grpcserver

import (
	"gateway/internal/ratelimit"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

// RateLimitStreamInterceptor 与 HTTP 共用 Limiter，按租户限流。
func RateLimitStreamInterceptor(l *ratelimit.Limiter) grpc.StreamServerInterceptor {
	return func(srv any, ss grpc.ServerStream, info *grpc.StreamServerInfo, handler grpc.StreamHandler) error {
		if l == nil {
			return handler(srv, ss)
		}
		tenant := tenantFromContext(ss.Context())
		switch l.Allow(ss.Context(), tenant) {
		case ratelimit.DailyExceeded:
			return status.Error(codes.ResourceExhausted, "daily quota exceeded")
		case ratelimit.QPSExceeded:
			return status.Error(codes.ResourceExhausted, "rate limit exceeded")
		default:
			return handler(srv, ss)
		}
	}
}
