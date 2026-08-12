package middleware

import (
	"strings"

	"github.com/gin-gonic/gin"
)

// BlockList 按前缀拦截；并通配拦截 /api/<service>/inner/**（内部写接口禁止经网关暴露）。
func BlockList(prefixes []string) gin.HandlerFunc {
	return func(c *gin.Context) {
		path := c.Request.URL.Path
		for _, p := range prefixes {
			if p != "" && strings.HasPrefix(path, p) {
				c.JSON(403, gin.H{"code": 403, "msg": "forbidden"})
				c.Abort()
				return
			}
		}
		if isAPIInnerPath(path) {
			c.JSON(403, gin.H{"code": 403, "msg": "forbidden"})
			c.Abort()
			return
		}
		c.Next()
	}
}

// isAPIInnerPath 匹配 /api/<一段服务名>/inner 及子路径，例如：
// /api/search/inner/index、/api/notify/inner/create。
func isAPIInnerPath(path string) bool {
	const apiPrefix = "/api/"
	if !strings.HasPrefix(path, apiPrefix) {
		return false
	}
	rest := path[len(apiPrefix):]
	slash := strings.IndexByte(rest, '/')
	if slash < 0 {
		return false
	}
	afterSvc := rest[slash+1:]
	return afterSvc == "inner" || strings.HasPrefix(afterSvc, "inner/")
}
