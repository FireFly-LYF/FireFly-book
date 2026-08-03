package middleware

import (
	"context"
	"strconv"
	"time"

	"gateway/internal/ratelimit"

	"github.com/gin-gonic/gin"
)

// RateLimit 返回 Gin 限流中间件，调用共享 Limiter（与 gRPC 共用租户配额）。
func RateLimit(l *ratelimit.Limiter) gin.HandlerFunc {
	return func(c *gin.Context) {
		tenantVal, exists := c.Get("tenant")
		if !exists {
			c.Next()
			return
		}
		tenant, _ := tenantVal.(string)

		switch l.Allow(context.Background(), tenant) {
		case ratelimit.DailyExceeded:
			abortDailyQuotaExceeded(c, l.DailyLimit())
		case ratelimit.QPSExceeded:
			qpsLimit := l.Capacity()
			if qpsLimit <= 0 {
				qpsLimit = l.Rate()
			}
			abortRateLimitExceeded(c, qpsLimit, l.Rate())
		default:
			c.Next()
		}
	}
}

func abortDailyQuotaExceeded(c *gin.Context, dailyLimit int64) {
	retryAfter, resetAt := secondsUntilMidnight()
	setRateLimitHeaders(c, dailyLimit, 0, retryAfter, resetAt)
	c.JSON(429, gin.H{"code": 429, "msg": "daily quota exceeded"})
	c.Abort()
}

func abortRateLimitExceeded(c *gin.Context, limit, rate int64) {
	retryAfter := int64(1)
	if rate > 0 {
		retryAfter = (int64(1) + rate - 1) / rate
	}
	setRateLimitHeaders(c, limit, 0, retryAfter, time.Now().Unix()+retryAfter)
	c.JSON(429, gin.H{"code": 429, "msg": "rate limit exceeded"})
	c.Abort()
}

func setRateLimitHeaders(c *gin.Context, limit, remaining, retryAfter, resetAt int64) {
	c.Header("X-RateLimit-Limit", strconv.FormatInt(limit, 10))
	c.Header("X-RateLimit-Remaining", strconv.FormatInt(remaining, 10))
	c.Header("X-RateLimit-Reset", strconv.FormatInt(resetAt, 10))
	if retryAfter > 0 {
		c.Header("Retry-After", strconv.FormatInt(retryAfter, 10))
	}
}

func secondsUntilMidnight() (retryAfter, resetAt int64) {
	now := time.Now()
	midnight := time.Date(now.Year(), now.Month(), now.Day()+1, 0, 0, 0, 0, now.Location())
	retryAfter = int64(midnight.Sub(now).Seconds())
	if retryAfter < 1 {
		retryAfter = 1
	}
	return retryAfter, midnight.Unix()
}
