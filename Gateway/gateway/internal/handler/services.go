package handler

import (
	"errors"

	"gateway/internal/registry"
	"gateway/internal/upstream"

	"github.com/gin-gonic/gin"
)

// ListServices 返回所有下游节点（含 unhealthy），供运维查看。
func (g *Gateway) ListServices(c *gin.Context) {
	if g.router != nil {
		c.JSON(200, gin.H{"code": 0, "msg": "ok", "data": g.router.ListAll()})
		return
	}
	c.JSON(200, gin.H{"code": 0, "msg": "ok", "data": g.reg.List()})
}

// ListRoutes 返回路径路由表（仅按路径转发模式）。
func (g *Gateway) ListRoutes(c *gin.Context) {
	if g.router == nil {
		c.JSON(200, gin.H{"code": 0, "msg": "ok", "data": []any{}})
		return
	}
	type item struct {
		ID           string `json:"id"`
		Prefix       string `json:"prefix"`
		AuthRequired bool   `json:"auth_required"`
		Nodes        int    `json:"nodes"`
	}
	var data []item
	for _, route := range g.router.Routes() {
		data = append(data, item{
			ID:           route.ID,
			Prefix:       route.Prefix,
			AuthRequired: route.AuthRequired,
			Nodes:        len(route.Reg.List()),
		})
	}
	c.JSON(200, gin.H{"code": 0, "msg": "ok", "data": data})
}

// RegisterService 动态注册下游；http / grpc / tcp / addr 至少填一项。
// 启用 routes 时必须传 route（路由 id 或 prefix），例如 {"http":"http://127.0.0.1:9001","route":"user"}。
func (g *Gateway) RegisterService(c *gin.Context) {
	var req struct {
		HTTP   string `json:"http"`
		GRPC   string `json:"grpc"`
		TCP    string `json:"tcp"`
		Addr   string `json:"addr"`
		Weight int    `json:"weight"`
		Route  string `json:"route"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(400, gin.H{"code": 400, "msg": "bad request"})
		return
	}
	ep, err := upstream.Normalize(req.HTTP, req.GRPC, req.TCP, req.Addr, req.Weight)
	if err != nil {
		c.JSON(400, gin.H{"code": 400, "msg": err.Error()})
		return
	}

	if g.router != nil {
		if req.Route == "" {
			c.JSON(400, gin.H{"code": 400, "msg": "route required when path routes enabled"})
			return
		}
		if err := g.router.RegisterTo(req.Route, ep); err != nil {
			if errors.Is(err, registry.ErrDuplicate) {
				c.JSON(409, gin.H{"code": 409, "msg": err.Error()})
				return
			}
			if errors.Is(err, registry.ErrNotFound) {
				c.JSON(404, gin.H{"code": 404, "msg": err.Error()})
				return
			}
			c.JSON(500, gin.H{"code": 500, "msg": err.Error()})
			return
		}
		c.JSON(200, gin.H{"code": 0, "msg": "ok", "data": gin.H{"id": ep.ID, "route": req.Route}})
		return
	}

	if err := g.reg.Register(ep); err != nil {
		if errors.Is(err, registry.ErrDuplicate) {
			c.JSON(409, gin.H{"code": 409, "msg": err.Error()})
			return
		}
		c.JSON(500, gin.H{"code": 500, "msg": err.Error()})
		return
	}
	registry.Sync(g.reg, g.balancers)
	c.JSON(200, gin.H{"code": 0, "msg": "ok", "data": gin.H{"id": ep.ID}})
}

// DeregisterService 按节点 id 下线；兼容 http 字段（与 id 等价）。
func (g *Gateway) DeregisterService(c *gin.Context) {
	var req struct {
		ID    string `json:"id"`
		HTTP  string `json:"http"`
		Route string `json:"route"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(400, gin.H{"code": 400, "msg": "bad request"})
		return
	}
	id := req.ID
	if id == "" {
		id = req.HTTP
	}
	if id == "" {
		c.JSON(400, gin.H{"code": 400, "msg": "id or http required"})
		return
	}

	if g.router != nil {
		if err := g.router.DeregisterFrom(req.Route, id); err != nil {
			if errors.Is(err, registry.ErrNotFound) {
				c.JSON(404, gin.H{"code": 404, "msg": err.Error()})
				return
			}
			c.JSON(500, gin.H{"code": 500, "msg": err.Error()})
			return
		}
		c.JSON(200, gin.H{"code": 0, "msg": "ok"})
		return
	}

	if err := g.reg.Deregister(id); err != nil {
		if errors.Is(err, registry.ErrNotFound) {
			c.JSON(404, gin.H{"code": 404, "msg": err.Error()})
			return
		}
		c.JSON(500, gin.H{"code": 500, "msg": err.Error()})
		return
	}
	registry.Sync(g.reg, g.balancers)
	c.JSON(200, gin.H{"code": 0, "msg": "ok"})
}
