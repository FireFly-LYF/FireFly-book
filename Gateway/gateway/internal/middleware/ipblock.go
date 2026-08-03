package middleware

import (
	"github.com/gin-gonic/gin"
)

// IPBlockList 拦截 listed IP；blocked 为空 map 时等价于不拦截。
func IPBlockList(blocked map[string]struct{}) gin.HandlerFunc {
	return func(c *gin.Context) {
		ip := c.ClientIP()
		if _, ok := blocked[ip]; ok {
			c.JSON(403, gin.H{"code": 403, "msg": "ip forbidden"})
			c.Abort()
			return
		}
		c.Next()
	}
}

// IPsToSet 把 []string 转成 map，便于 O(1) 查找。
func IPsToSet(ips []string) map[string]struct{} {
	m := make(map[string]struct{}, len(ips))
	for _, ip := range ips {
		//将ip转换为map，只需要键，不需要值
		m[ip] = struct{}{}
	}
	return m
}
