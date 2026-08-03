package middleware

import (
	"gateway/internal/auth"
	"gateway/internal/tenant"

	"github.com/gin-gonic/gin"
)

func JWTAuth(secret []byte) gin.HandlerFunc {
	return func(c *gin.Context) {
		tokenStr, ok := auth.ExtractBearer(c)
		if !ok {
			c.JSON(401, gin.H{"code": 401, "msg": "missing token"})
			c.Abort()
			return
		}
		//解析 token
		claims, err := auth.Parse(tokenStr, secret)
		if err != nil {
			c.JSON(401, gin.H{"code": 401, "msg": "invalid token"})
			c.Abort()
			return
		}
		//验证 tenant
		if !tenant.IsValid(claims.Tenant) {
			c.JSON(401, gin.H{"code": 401, "msg": "unknown tenant"})
			c.Abort()
			return
		}
		c.Set("tenant", claims.Tenant)
		c.Next()
	}
}
