package handler

import (
	"time"

	redisx "gateway/internal/redis"

	"github.com/gin-gonic/gin"
)

func (g *Gateway) GetStatistics(c *gin.Context) {
	tenant := c.Query("tenant")
	if tenant == "" {
		c.JSON(400, gin.H{"code": 400, "msg": "tenant required"})
		return
	}
	dateStr := c.DefaultQuery("date", time.Now().Format("20060102"))
	day, err := time.Parse("20060102", dateStr)
	if err != nil {
		c.JSON(400, gin.H{"code": 400, "msg": "bad date"})
		return
	}
	count, err := redisx.Get(c.Request.Context(), g.redis, tenant, day)
	if err != nil {
		c.JSON(500, gin.H{"code": 500, "msg": "redis error"})
		return
	}
	c.JSON(200, gin.H{
		"code": 0, "msg": "ok",
		"data": gin.H{"tenant": tenant, "date": dateStr, "count": count},
	})
}

// GetStatisticsReport 返回指定租户最近 7 天的日请求量数组。
func (g *Gateway) GetStatisticsReport(c *gin.Context) {
	tenant := c.Query("tenant")
	if tenant == "" {
		c.JSON(400, gin.H{"code": 400, "msg": "tenant required"})
		return
	}
	if g.redis == nil {
		c.JSON(503, gin.H{"code": 503, "msg": "redis unavailable"})
		return
	}
	days, err := redisx.GetLastNDays(c.Request.Context(), g.redis, tenant, 7)
	if err != nil {
		c.JSON(500, gin.H{"code": 500, "msg": "redis error"})
		return
	}
	c.JSON(200, gin.H{
		"code": 0, "msg": "ok",
		"data": gin.H{"tenant": tenant, "days": days},
	})
}
