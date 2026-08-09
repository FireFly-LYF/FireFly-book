package middleware

import (
	"gateway/internal/auth"
	"gateway/internal/tenant"
	"strings"

	"github.com/gin-gonic/gin"
)

func JWTAuth(secret []byte) gin.HandlerFunc {
	return jwtAuth(secret, true, nil)
}

// OptionalJWTAuth 开发联调：无 Token 放行；有 Token 则校验。
func OptionalJWTAuth(secret []byte) gin.HandlerFunc {
	return jwtAuth(secret, false, nil)
}

// JWTAuthExcept 强制 JWT，但 skip 中的「METHOD path」放行（如 POST /api/user/login）。
func JWTAuthExcept(secret []byte, skip map[string]struct{}) gin.HandlerFunc {
	return jwtAuth(secret, true, skip)
}

func jwtAuth(secret []byte, required bool, skip map[string]struct{}) gin.HandlerFunc {
	return func(c *gin.Context) {
		if skip != nil {
			key := c.Request.Method + " " + c.Request.URL.Path
			if _, ok := skip[key]; ok {
				c.Next()
				return
			}
			// Gin group /api 下 path 仍是完整路径
			if _, ok := skip[strings.ToUpper(c.Request.Method)+" "+c.Request.URL.Path]; ok {
				c.Next()
				return
			}
		}

		tokenStr, ok := auth.ExtractBearer(c)
		if !ok {
			if required {
				c.JSON(401, gin.H{"code": 401, "msg": "missing token"})
				c.Abort()
				return
			}
			c.Next()
			return
		}
		claims, err := auth.Parse(tokenStr, secret)
		if err != nil {
			c.JSON(401, gin.H{"code": 401, "msg": "invalid token"})
			c.Abort()
			return
		}
		t := claims.Tenant()
		if !tenant.IsValid(t) {
			c.JSON(401, gin.H{"code": 401, "msg": "unknown tenant"})
			c.Abort()
			return
		}
		c.Set("tenant", t)
		if claims.UserID != "" {
			c.Set("userId", claims.UserID)
		} else if claims.Subject != "" {
			c.Set("userId", claims.Subject)
		}
		c.Next()
	}
}
