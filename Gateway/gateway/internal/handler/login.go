package handler

import (
	"gateway/internal/auth"
	"gateway/internal/tenant"

	"github.com/gin-gonic/gin"
)

func (g *Gateway) Login(c *gin.Context) {
	var req struct {
		Tenant string `json:"tenant"`
	}
	if err := c.BindJSON(&req); err != nil || !tenant.IsValid(req.Tenant) {
		c.JSON(400, gin.H{"code": 400, "msg": "bad tenant"})
		return
	}
	token, err := auth.Sign(req.Tenant, g.cfg.TokenTTLDuration(), g.cfg.JWTSecret())
	if err != nil {
		c.JSON(500, gin.H{"code": 500, "msg": "sign failed"})
		return
	}
	c.JSON(200, gin.H{"code": 0, "msg": "ok", "data": gin.H{"token": token}})
}
