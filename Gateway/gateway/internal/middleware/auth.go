package middleware

import (
	"gateway/internal/auth"
	"gateway/internal/tenant"

	"github.com/gin-gonic/gin"
)

func JWTAuth(secret []byte) gin.HandlerFunc {
	return jwtAuth(secret, true)
}

// OptionalJWTAuth 开发联调：无 Token 放行；有 Token 则校验。
func OptionalJWTAuth(secret []byte) gin.HandlerFunc {
	return jwtAuth(secret, false)
}

func jwtAuth(secret []byte, required bool) gin.HandlerFunc {
	return func(c *gin.Context) {
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
		if !tenant.IsValid(claims.Tenant) {
			c.JSON(401, gin.H{"code": 401, "msg": "unknown tenant"})
			c.Abort()
			return
		}
		c.Set("tenant", claims.Tenant)
		if claims.UserID != "" {
			c.Set("userId", claims.UserID)
		}
		c.Next()
	}
}
