package middleware

import (
	"strings"

	"github.com/gin-gonic/gin"
)

func BlockInternal() gin.HandlerFunc {
	return func(c *gin.Context) {
		if strings.HasPrefix(c.Request.URL.Path, "/api/internal") {
			c.JSON(403, gin.H{"code": 1, "msg": "forbidden"})
			c.Abort()
			return
		}
		c.Next()
	}
}
