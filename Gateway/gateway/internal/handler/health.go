package handler

import "github.com/gin-gonic/gin"

func (g *Gateway) Health(c *gin.Context) {
	c.JSON(200, gin.H{"code": 0, "msg": "ok", "data": "gateway healthy"})
}
