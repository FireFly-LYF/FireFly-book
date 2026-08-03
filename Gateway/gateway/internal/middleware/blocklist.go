package middleware

import (
	"strings"

	"github.com/gin-gonic/gin"
)

func BlockList(prefixes []string) gin.HandlerFunc {
	return func(c *gin.Context) {
		path := c.Request.URL.Path
		for _, p := range prefixes {
			if strings.HasPrefix(path, p) {
				c.JSON(403, gin.H{"code": 403, "msg": "forbidden"})
				c.Abort()
				return
			}
		}
		c.Next()
	}
}
