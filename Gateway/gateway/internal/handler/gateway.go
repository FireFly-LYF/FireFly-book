package handler

import (
	"gateway/internal/config"
	"gateway/internal/middleware"
	"gateway/internal/proxy"
	"gateway/internal/ratelimit"
	redisx "gateway/internal/redis"
	"gateway/internal/registry"
	"net/http"
	"os"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/redis/go-redis/v9"
)

type Gateway struct {
	cfg       *config.Config
	redis     *redis.Client
	limiter   *ratelimit.Limiter
	breaker   *registry.CircuitBreaker
	stats     *redisx.StatsRecorder
	reg       registry.Registry
	balancers *registry.Balancers
}

func New(cfg *config.Config, rdb *redis.Client, limiter *ratelimit.Limiter, cb *registry.CircuitBreaker, rec *redisx.StatsRecorder, reg registry.Registry, bs *registry.Balancers) *Gateway {
	return &Gateway{cfg: cfg, redis: rdb, limiter: limiter, breaker: cb, stats: rec, reg: reg, balancers: bs}
}

func (g *Gateway) Register(r *gin.Engine) {
	r.Use(gin.Recovery())
	r.Use(middleware.RequestLogger())
	r.Use(middleware.IPBlockList(middleware.IPsToSet(g.cfg.Security.IPBlocklist)))

	r.POST("/gateway/login", g.Login)
	r.GET("/gateway/health", g.Health)

	admin := r.Group("/gateway")
	admin.Use(middleware.JWTAuth(g.cfg.JWTSecret()))
	admin.GET("/services", g.ListServices)
	admin.POST("/services", g.RegisterService)
	admin.DELETE("/services", g.DeregisterService)
	admin.GET("/statistic", g.GetStatistics)
	admin.GET("/statistics/report", g.GetStatisticsReport)

	api := r.Group("/api")
	api.Use(middleware.JWTAuth(g.cfg.JWTSecret()))
	api.Use(middleware.BlockList([]string{"/api/internal"}))
	api.Use(middleware.RateLimit(g.limiter))
	api.Use(middleware.TrafficStats(g.stats))
	api.Any("/*path", proxy.Handler(g.balancers.HTTP, g.breaker))

	g.registerAdmin(r)
}

func (g *Gateway) registerAdmin(r *gin.Engine) {
	// Docker 里 dist 放 /app/admin；本地测试可设环境变量覆盖
	dir := os.Getenv("ADMIN_STATIC_DIR")
	if dir == "" {
		dir = "/app/admin"
	}
	// 目录不存在就跳过（本地 go run 没打包 Admin 时不影响网关 API）
	if _, err := os.Stat(dir); err != nil {
		return
	}
	// Vite 打包的 JS/CSS 在 /assets 下
	r.Static("/assets", dir+"/assets")
	r.StaticFile("/favicon.svg", dir+"/favicon.svg")
	// Vue Router 用 history 模式：/login、/services 等路径都要返回 index.html
	r.NoRoute(func(c *gin.Context) {
		path := c.Request.URL.Path
		// /gateway、/api 走原来的 404，不要误返回网页
		if strings.HasPrefix(path, "/gateway") || strings.HasPrefix(path, "/api") {
			c.JSON(http.StatusNotFound, gin.H{"code": 404, "msg": "not found"})
			return
		}
		c.File(dir + "/index.html")
	})
}
