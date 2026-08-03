package grpcserver

import (
	redisx "gateway/internal/redis"

	"google.golang.org/grpc"
)

// StatsStreamInterceptor RPC 成功后按租户累加流量统计。
func StatsStreamInterceptor(rec *redisx.StatsRecorder) grpc.StreamServerInterceptor {
	return func(srv any, ss grpc.ServerStream, info *grpc.StreamServerInfo, handler grpc.StreamHandler) error {
		err := handler(srv, ss)
		if err == nil && rec != nil {
			rec.Record(ss.Context(), tenantFromContext(ss.Context()))
		}
		return err
	}
}
