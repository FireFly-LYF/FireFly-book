package handler

import (
	"gateway/internal/auth"
	"gateway/internal/tenant"

	"github.com/gin-gonic/gin"
)

// Login 曾用于 POST /gateway/login（仅校验租户名即签发 JWT）。
// 路由已在 Register 中禁用，保留函数供测试/文档对照，勿重新挂载到生产。
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
