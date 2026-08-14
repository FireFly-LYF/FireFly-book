package handler

import (
	"crypto/subtle"

	"gateway/internal/auth"
	"gateway/internal/tenant"

	"github.com/gin-gonic/gin"
)

// AdminLogin POST /gateway/login：校验运维口令后签发 typ=admin 的 Token（admin.jwt_secret）。
// 业务 Access Token 使用另一套密钥，无法调用 /gateway/* 运维接口。
func (g *Gateway) AdminLogin(c *gin.Context) {
	var req struct {
		Tenant   string `json:"tenant"`
		Password string `json:"password"`
	}
	if err := c.BindJSON(&req); err != nil || !tenant.IsValid(req.Tenant) {
		c.JSON(400, gin.H{"code": 400, "msg": "bad tenant"})
		return
	}
	want := g.cfg.AdminPassword()
	if subtle.ConstantTimeCompare([]byte(req.Password), []byte(want)) != 1 {
		c.JSON(401, gin.H{"code": 401, "msg": "invalid admin credentials"})
		return
	}
	token, err := auth.SignAdmin(req.Tenant, g.cfg.AdminTokenTTLDuration(), g.cfg.AdminJWTSecret())
	if err != nil {
		c.JSON(500, gin.H{"code": 500, "msg": "sign failed"})
		return
	}
	c.JSON(200, gin.H{"code": 0, "msg": "ok", "data": gin.H{"token": token}})
}
