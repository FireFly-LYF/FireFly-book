package middleware

import (
	redisx "gateway/internal/redis"

	"github.com/gin-gonic/gin"
)

// TrafficStats 成功请求后按租户计数，与 gRPC StatsUnaryInterceptor 共用 StatsRecorder。
func TrafficStats(rec *redisx.StatsRecorder) gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Next()
		if rec == nil {
			return
		}
		if c.Writer.Status() < 200 || c.Writer.Status() >= 300 {
			return
		}
		tenantVal, ok := c.Get("tenant")
		if !ok {
			return
		}
		tenant, _ := tenantVal.(string)
		rec.Record(c.Request.Context(), tenant)
	}
}
