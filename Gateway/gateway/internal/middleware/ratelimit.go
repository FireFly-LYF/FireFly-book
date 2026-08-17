package middleware

import (
	"context"
	"fmt"
	"strconv"
	"time"

	"gateway/internal/ratelimit"

	"github.com/gin-gonic/gin"
)

// RateLimit 返回 Gin 限流中间件。
// 限流键：user_ip（有 userId 用用户，否则 IP）| ip | tenant；配额按路径最长前缀匹配。
func RateLimit(l *ratelimit.Limiter) gin.HandlerFunc {
	return func(c *gin.Context) {
		if l == nil {
			c.Next()
			return
		}
		key := resolveRateLimitKey(c, l.KeyBy())
		rate, capacity, scope := l.ResolveQuota(c.Request.URL.Path)
		// 身份 + 路由作用域隔离 QPS 桶（登录严、普通 API 松，互不抢配额）
		bucketKey := key + "|" + scope

		switch l.AllowQuota(context.Background(), key, bucketKey, rate, capacity) {
		case ratelimit.DailyExceeded:
			abortDailyQuotaExceeded(c, l.DailyLimit())
		case ratelimit.QPSExceeded:
			abortRateLimitExceeded(c, capacity, rate)
		default:
			c.Next()
		}
	}
}

func resolveRateLimitKey(c *gin.Context, keyBy string) string {
	switch keyBy {
	case "ip":
		return ipKey(c)
	case "tenant":
		if v, ok := c.Get("tenant"); ok {
			if t, _ := v.(string); t != "" {
				return "tenant:" + t
			}
		}
		return ipKey(c)
	default: // user_ip
		if v, ok := c.Get("userId"); ok {
			if id, _ := v.(string); id != "" {
				return "user:" + id
			}
			if id := fmt.Sprint(v); id != "" && id != "<nil>" {
				return "user:" + id
			}
		}
		return ipKey(c)
	}
}

func ipKey(c *gin.Context) string {
	ip := c.ClientIP()
	if ip == "" {
		return "ip:unknown"
	}
	return "ip:" + ip
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
		if retryAfter < 1 {
			retryAfter = 1
		}
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
