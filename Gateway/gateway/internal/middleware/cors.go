package middleware

import (
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
)

// CORS 仅对白名单 Origin 回写 Allow-Origin；未命中则不写允许头（浏览器拦截跨域）。
// allowCredentials 仅在 Origin 命中时生效（规范禁止 * + credentials）。
func CORS(allowedOrigins []string, allowCredentials bool) gin.HandlerFunc {
	allowed := make(map[string]struct{}, len(allowedOrigins))
	for _, o := range allowedOrigins {
		o = strings.TrimRight(strings.TrimSpace(o), "/")
		if o != "" && o != "*" {
			allowed[o] = struct{}{}
		}
	}

	return func(c *gin.Context) {
		origin := strings.TrimRight(strings.TrimSpace(c.GetHeader("Origin")), "/")
		ok := false
		if origin != "" {
			_, ok = allowed[origin]
		}

		if ok {
			c.Header("Access-Control-Allow-Origin", origin)
			c.Header("Vary", "Origin")
			if allowCredentials {
				c.Header("Access-Control-Allow-Credentials", "true")
			}
			c.Header("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Request-Id, Idempotency-Key")
			c.Header("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS")
		}

		if c.Request.Method == http.MethodOptions {
			if origin != "" && !ok {
				c.AbortWithStatus(http.StatusForbidden)
				return
			}
			c.AbortWithStatus(http.StatusNoContent)
			return
		}
		c.Next()
	}
}
