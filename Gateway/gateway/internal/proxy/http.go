package proxy

import (
	"context"
	"net/http"
	"net/http/httputil"
	"net/url"
	"strings"
	"sync"

	"gateway/internal/lb"
	"gateway/internal/registry"

	"github.com/gin-gonic/gin"
)

// Handler 返回 HTTP 反向代理 Gin 中间件。
//
// 请求链路：
//  1. pickTarget：按客户端 IP 做负载均衡，选出下游 base URL
//  2. proxyReq：把 tenant / clientIP 写入 request.Context
//  3. ReverseProxy.ServeHTTP → Rewrite(rewrite)：改 URL + 改 Header 后转发
//
// 每个 target 缓存一个 ReverseProxy 实例，避免每次请求重复 url.Parse。
func Handler(b lb.Balancer, cb *registry.CircuitBreaker) gin.HandlerFunc {
	var mu sync.Mutex
	cache := map[string]*httputil.ReverseProxy{}

	// getProxy 按下游地址懒加载并缓存 ReverseProxy。
	// Rewrite 回调在每次转发时执行，通过 pr.In.Context() 读取本次请求的租户与 IP。
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
			//告诉 ReverseProxy“转发前请调用这个函数
			Rewrite: func(pr *httputil.ProxyRequest) {
				//header重写、url重写
				rewrite(pr, remote)
			},
		}
		cache[target] = p
		return p, nil
	}

	return func(c *gin.Context) {
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
		// 用带 context 的 request 发起代理；Rewrite 从 pr.In.Context() 取租户与 IP。
		p.ServeHTTP(c.Writer, proxyReq(c))
		if cb != nil {
			cb.Record(target, c.Writer.Status() >= 500)
		}
	}
}

// proxyReq 把 Gin 中间件链上的 tenant / clientIP 注入 request.Context。
func proxyReq(c *gin.Context) *http.Request {
	req := c.Request
	ctx := req.Context()

	//把租户和 IP 放进 request.Context()
	if tenant, ok := c.Get("tenant"); ok {
		if t, ok := tenant.(string); ok && t != "" {
			ctx = context.WithValue(ctx, "tenant", t)
		}
	}
	if ip := c.ClientIP(); ip != "" {
		ctx = context.WithValue(ctx, "clientIP", ip)
	}

	// 无新数据时不克隆 request，避免不必要的分配。
	if ctx == req.Context() {
		return req
	}
	return req.WithContext(ctx)
}

// rewrite 在转发前修改出站请求的 URL 与 Header。
//
// pr.In  = 客户端原始请求（进网关）
// pr.Out = 发往下游的请求（出网关）
func rewrite(pr *httputil.ProxyRequest, remote *url.URL) {
	// --- URL 重写 ---
	pr.SetURL(remote) // 设置下游 scheme/host，Host 头会随之更新
	// 剥离网关对外前缀 /api：/api/user → /user
	pr.Out.URL.Path = strings.TrimPrefix(pr.In.URL.Path, "/api")
	if pr.Out.URL.Path == "" {
		pr.Out.URL.Path = "/"
	}

	// --- Header 修改 ---

	// 1. 剥离 Authorization：JWT 仅供网关鉴权，不应透传给下游（防泄露、防下游误用）
	pr.Out.Header.Del("Authorization")

	// 2. 注入租户：下游可直接读 X-Tenant-Id 做数据隔离，无需再解析 JWT
	if tenant, ok := pr.In.Context().Value("tenant").(string); ok && tenant != "" {
		pr.Out.Header.Set("X-Tenant-Id", tenant)
	}

	// 3. 传递客户端真实 IP：下游做日志/限流/地域判断时使用
	if clientIP, ok := pr.In.Context().Value("clientIP").(string); ok && clientIP != "" {
		pr.Out.Header.Set("X-Real-IP", clientIP)
		// 若上游已有 X-Forwarded-For（多级代理），追加而非覆盖，保留完整链路
		if prior := pr.In.Header.Get("X-Forwarded-For"); prior != "" {
			pr.Out.Header.Set("X-Forwarded-For", prior+", "+clientIP)
		} else {
			pr.Out.Header.Set("X-Forwarded-For", clientIP)
		}
	}
}

// pickTarget 以客户端 IP 为 LB hash key；IP 不可用时退化为请求路径。
func pickTarget(c *gin.Context, b lb.Balancer) (string, bool) {
	key := c.ClientIP()
	if key == "" {
		key = c.Request.URL.Path
	}
	return b.NextKey(key)
}
