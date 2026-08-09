package proxy

import (
	"context"
	"fmt"
	"net/http"
	"net/http/httputil"
	"net/url"
	"strings"
	"sync"

	"gateway/internal/lb"
	"gateway/internal/registry"

	"github.com/gin-gonic/gin"
)

type ctxKey string

const (
	ctxTenant   ctxKey = "tenant"
	ctxUserID   ctxKey = "userId"
	ctxClientIP ctxKey = "clientIP"
)

// Handler 返回 HTTP 反向代理 Gin 中间件（单一 LB 池，兼容旧配置）。
func Handler(b lb.Balancer, cb *registry.CircuitBreaker) gin.HandlerFunc {
	return HandlerWithOptions(b, cb, "")
}

// HandlerWithOptions 支持配置路径剥离前缀。
// stripPrefix 非空时剥离（如 /api）；空则保留完整路径（FireFly Java Controller）。
func HandlerWithOptions(b lb.Balancer, cb *registry.CircuitBreaker, stripPrefix string) gin.HandlerFunc {
	return newProxyHandler(func(c *gin.Context) (lb.Balancer, bool) {
		return b, b != nil
	}, cb, stripPrefix)
}

// RouteHandler 按路径前缀选择不同 LB 池。
func RouteHandler(router *registry.HTTPRouter, cb *registry.CircuitBreaker, stripPrefix string) gin.HandlerFunc {
	return newProxyHandler(func(c *gin.Context) (lb.Balancer, bool) {
		route, ok := router.Match(c.Request.URL.Path)
		if !ok {
			return nil, false
		}
		return route.Balancers.HTTP, true
	}, cb, stripPrefix)
}

func newProxyHandler(pickBalancer func(*gin.Context) (lb.Balancer, bool), cb *registry.CircuitBreaker, stripPrefix string) gin.HandlerFunc {
	var mu sync.Mutex
	cache := map[string]*httputil.ReverseProxy{}

	getProxy := func(target string) (*httputil.ReverseProxy, error) {
		mu.Lock()
		defer mu.Unlock()
		if p, ok := cache[target]; ok {
			return p, nil
		}
		remote, err := url.Parse(target)
		if err != nil {
			return nil, err
		}
		p := &httputil.ReverseProxy{
			Rewrite: func(pr *httputil.ProxyRequest) {
				rewrite(pr, remote, stripPrefix)
			},
		}
		cache[target] = p
		return p, nil
	}

	return func(c *gin.Context) {
		b, ok := pickBalancer(c)
		if !ok {
			c.JSON(404, gin.H{"code": 404, "msg": "no route for path"})
			return
		}
		target, ok := pickTarget(c, b)
		if !ok {
			c.JSON(503, gin.H{"code": 503, "msg": "no upstream available"})
			return
		}
		if cb != nil && !cb.Allow(target) {
			c.JSON(503, gin.H{"code": 503, "msg": "circuit open"})
			return
		}
		p, err := getProxy(target)
		if err != nil {
			c.JSON(502, gin.H{"code": 502, "msg": "bad upstream"})
			return
		}
		p.ServeHTTP(c.Writer, proxyReq(c))
		if cb != nil {
			cb.Record(target, c.Writer.Status() >= 500)
		}
	}
}

// proxyReq 把 Gin 中间件链上的 tenant / clientIP / userId 注入 request.Context。
func proxyReq(c *gin.Context) *http.Request {
	req := c.Request
	ctx := req.Context()

	if tenant, ok := c.Get("tenant"); ok {
		if t, ok := tenant.(string); ok && t != "" {
			ctx = context.WithValue(ctx, ctxTenant, t)
		}
	}
	if userID, ok := c.Get("userId"); ok {
		if s := anyToString(userID); s != "" {
			ctx = context.WithValue(ctx, ctxUserID, s)
		}
	}
	if ip := c.ClientIP(); ip != "" {
		ctx = context.WithValue(ctx, ctxClientIP, ip)
	}

	if ctx == req.Context() {
		return req
	}
	return req.WithContext(ctx)
}

func anyToString(v any) string {
	switch t := v.(type) {
	case string:
		return t
	case fmt.Stringer:
		return t.String()
	default:
		return fmt.Sprint(v)
	}
}

// rewrite 在转发前修改出站请求的 URL 与 Header。
func rewrite(pr *httputil.ProxyRequest, remote *url.URL, stripPrefix string) {
	pr.SetURL(remote)

	path := pr.In.URL.Path
	if stripPrefix != "" {
		path = strings.TrimPrefix(path, stripPrefix)
		if path == "" {
			path = "/"
		}
	}
	pr.Out.URL.Path = path
	pr.Out.URL.RawQuery = pr.In.URL.RawQuery

	pr.Out.Header.Del("Authorization")

	if tenant, ok := pr.In.Context().Value(ctxTenant).(string); ok && tenant != "" {
		pr.Out.Header.Set("X-Tenant-Id", tenant)
	}

	// 防伪造：先清客户端 X-User-Id，再写入网关从 JWT 解析的 uid
	pr.Out.Header.Del("X-User-Id")
	if userID, ok := pr.In.Context().Value(ctxUserID).(string); ok && userID != "" {
		pr.Out.Header.Set("X-User-Id", userID)
	}

	if clientIP, ok := pr.In.Context().Value(ctxClientIP).(string); ok && clientIP != "" {
		pr.Out.Header.Set("X-Real-IP", clientIP)
		if prior := pr.In.Header.Get("X-Forwarded-For"); prior != "" {
			pr.Out.Header.Set("X-Forwarded-For", prior+", "+clientIP)
		} else {
			pr.Out.Header.Set("X-Forwarded-For", clientIP)
		}
	}
}

func pickTarget(c *gin.Context, b lb.Balancer) (string, bool) {
	key := c.ClientIP()
	if key == "" {
		key = c.Request.URL.Path
	}
	return b.NextKey(key)
}
