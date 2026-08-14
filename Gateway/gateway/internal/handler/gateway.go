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
	router    *registry.HTTPRouter
}

func New(cfg *config.Config, rdb *redis.Client, limiter *ratelimit.Limiter, cb *registry.CircuitBreaker, rec *redisx.StatsRecorder, reg registry.Registry, bs *registry.Balancers, router *registry.HTTPRouter) *Gateway {
	return &Gateway{cfg: cfg, redis: rdb, limiter: limiter, breaker: cb, stats: rec, reg: reg, balancers: bs, router: router}
}

func (g *Gateway) Register(r *gin.Engine) {
	r.Use(gin.Recovery())
	r.Use(middleware.CORS(g.cfg.CORS.AllowedOrigins, g.cfg.CORS.AllowCredentials))
	r.Use(middleware.RequestLogger())
	r.Use(middleware.IPBlockList(middleware.IPsToSet(g.cfg.Security.IPBlocklist)))

	// 运维登录：独立口令 + admin.jwt_secret；业务 Access 不可用此处 Token 调业务 API，也不可反向调运维 API
	r.POST("/gateway/login", g.AdminLogin)
	r.GET("/gateway/health", g.Health)

	admin := r.Group("/gateway")
	admin.Use(middleware.AdminJWTAuth(g.cfg.AdminJWTSecret()))
	admin.GET("/services", g.ListServices)
	admin.POST("/services", g.RegisterService)
	admin.DELETE("/services", g.DeregisterService)
	admin.GET("/routes", g.ListRoutes)
	admin.GET("/statistic", g.GetStatistics)
	admin.GET("/statistics/report", g.GetStatisticsReport)

	// 业务登录/注册/刷新公开；其余 /api 默认强制 Access JWT（jwt.api_required）
	publicAPI := map[string]struct{}{
		"POST /api/user/register": {},
		"POST /api/user/login":    {},
		"POST /api/user/refresh":  {},
	}
	var apiAuth gin.HandlerFunc
	if g.cfg.APIAuthRequired() {
		apiAuth = middleware.JWTAuthExcept(g.cfg.JWTSecret(), publicAPI)
	} else {
		apiAuth = middleware.OptionalJWTAuth(g.cfg.JWTSecret())
	}

	strip := g.cfg.StripPrefix()
	hmacSecret := g.cfg.InternalHMACSecret()
	var proxyHandler gin.HandlerFunc
	if g.router != nil {
		proxyHandler = proxy.RouteHandler(g.router, g.breaker, strip, hmacSecret)
	} else {
		proxyHandler = proxy.HandlerWithOptions(g.balancers.HTTP, g.breaker, strip, hmacSecret)
	}

	api := r.Group("/api")
	api.Use(apiAuth)
	// 显式前缀 + 通配 /api/<svc>/inner/**（见 middleware.BlockList）
	api.Use(middleware.BlockList([]string{
		"/api/internal",
		"/api/search/inner",
		"/api/notify/inner",
	}))
	api.Use(middleware.RateLimit(g.limiter))
	api.Use(middleware.TrafficStats(g.stats))
	api.Any("/*path", proxyHandler)

	// 媒体静态文件：/files/** → media-service（JWT 可选；真实访问控制靠签名 URL）
	if g.router != nil {
		if _, ok := g.router.Get("media-files"); ok {
			files := r.Group("/files")
			files.Use(middleware.OptionalJWTAuth(g.cfg.JWTSecret()))
			files.Use(middleware.RateLimit(g.limiter))
			files.Any("/*path", proxyHandler)
			files.Any("", proxyHandler)
		}
	}

	g.registerAdmin(r)
}

func (g *Gateway) registerAdmin(r *gin.Engine) {
	dir := os.Getenv("ADMIN_STATIC_DIR")
	if dir == "" {
		dir = "/app/admin"
	}
	if _, err := os.Stat(dir); err != nil {
		return
	}
	r.Static("/assets", dir+"/assets")
	r.StaticFile("/favicon.svg", dir+"/favicon.svg")
	r.NoRoute(func(c *gin.Context) {
		path := c.Request.URL.Path
		if strings.HasPrefix(path, "/gateway") || strings.HasPrefix(path, "/api") || strings.HasPrefix(path, "/files") {
			c.JSON(http.StatusNotFound, gin.H{"code": 404, "msg": "not found"})
			return
		}
		c.File(dir + "/index.html")
	})
}
