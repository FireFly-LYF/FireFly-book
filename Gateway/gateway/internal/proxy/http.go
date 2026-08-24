package proxy

import (
	"context"
	"errors"
	"fmt"
	"net"
	"net/http"
	"net/http/httputil"
	"net/url"
	"strings"
	"sync"
	"time"

	"gateway/internal/auth"
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
func Handler(b lb.Balancer, cb *registry.CircuitBreaker, hmacSecret []byte, responseHeaderTimeout time.Duration) gin.HandlerFunc {
	return HandlerWithOptions(b, cb, "", hmacSecret, responseHeaderTimeout)
}

// HandlerWithOptions 支持配置路径剥离前缀。
// stripPrefix 非空时剥离（如 /api）；空则保留完整路径（FireFly Java Controller）。
func HandlerWithOptions(b lb.Balancer, cb *registry.CircuitBreaker, stripPrefix string, hmacSecret []byte, responseHeaderTimeout time.Duration) gin.HandlerFunc {
	return newProxyHandler(func(c *gin.Context) (lb.Balancer, bool) {
		return b, b != nil
	}, cb, stripPrefix, hmacSecret, responseHeaderTimeout)
}

// RouteHandler 按路径前缀选择不同 LB 池。
func RouteHandler(router *registry.HTTPRouter, cb *registry.CircuitBreaker, stripPrefix string, hmacSecret []byte, responseHeaderTimeout time.Duration) gin.HandlerFunc {
	return newProxyHandler(func(c *gin.Context) (lb.Balancer, bool) {
		route, ok := router.Match(c.Request.URL.Path)
		if !ok {
			return nil, false
		}
		return route.Balancers.HTTP, true
	}, cb, stripPrefix, hmacSecret, responseHeaderTimeout)
}

func newProxyHandler(pickBalancer func(*gin.Context) (lb.Balancer, bool), cb *registry.CircuitBreaker, stripPrefix string, hmacSecret []byte, responseHeaderTimeout time.Duration) gin.HandlerFunc {
	if responseHeaderTimeout <= 0 {
		responseHeaderTimeout = 10 * time.Second
	}
	transport := newProxyTransport(responseHeaderTimeout)

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
		secret := append([]byte(nil), hmacSecret...)
		p := &httputil.ReverseProxy{
			Rewrite: func(pr *httputil.ProxyRequest) {
				rewrite(pr, remote, stripPrefix, secret)
			},
			Transport:    transport,
			ErrorHandler: proxyErrorHandler,
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
			// 5xx（含超时 504）计入熔断；连续失败达阈值后开路
			cb.Record(target, c.Writer.Status() >= 500)
		}
	}
}

// newProxyTransport 出站 Transport：拨号超时 + 等待响应头超时（慢上游不再无限占连接）。
// 仅限制「等到响应头」；响应体仍可流式转发（适合 /files 大文件）。
func newProxyTransport(responseHeaderTimeout time.Duration) *http.Transport {
	return &http.Transport{
		Proxy: http.ProxyFromEnvironment,
		DialContext: (&net.Dialer{
			Timeout:   3 * time.Second,
			KeepAlive: 30 * time.Second,
		}).DialContext,
		ForceAttemptHTTP2:     true,
		MaxIdleConns:          100,
		MaxIdleConnsPerHost:   20,
		IdleConnTimeout:       90 * time.Second,
		TLSHandshakeTimeout:   5 * time.Second,
		ExpectContinueTimeout: 1 * time.Second,
		ResponseHeaderTimeout: responseHeaderTimeout,
	}
}

// proxyErrorHandler 上游不可达 / 超时 → 502/504，供后续 cb.Record 按 status>=500 记失败。
func proxyErrorHandler(rw http.ResponseWriter, _ *http.Request, err error) {
	status := http.StatusBadGateway
	if isTimeoutErr(err) {
		status = http.StatusGatewayTimeout
	}
	http.Error(rw, http.StatusText(status), status)
}

func isTimeoutErr(err error) bool {
	if err == nil {
		return false
	}
	if errors.Is(err, context.DeadlineExceeded) {
		return true
	}
	var ne net.Error
	return errors.As(err, &ne) && ne.Timeout()
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
func rewrite(pr *httputil.ProxyRequest, remote *url.URL, stripPrefix string, hmacSecret []byte) {
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

	// 防伪造：清客户端身份头，再由网关写入并 HMAC 签名
	pr.Out.Header.Del("X-User-Id")
	pr.Out.Header.Del("X-Gateway-Ts")
	pr.Out.Header.Del("X-Gateway-Sign")

	userID := ""
	if uid, ok := pr.In.Context().Value(ctxUserID).(string); ok && uid != "" {
		userID = uid
		pr.Out.Header.Set("X-User-Id", userID)
	}

	if len(hmacSecret) > 0 {
		ts := time.Now().Unix()
		pr.Out.Header.Set("X-Gateway-Ts", auth.FormatTs(ts))
		pr.Out.Header.Set("X-Gateway-Sign", auth.SignInternal(hmacSecret, userID, ts))
	}

	if clientIP, ok := pr.In.Context().Value(ctxClientIP).(string); ok && clientIP != "" {
		pr.Out.Header.Set("X-Real-IP", clientIP)
		if prior := pr.In.Header.Get("X-Forwarded-For"); prior != "" {
			pr.Out.Header.Set("X-Forwarded-For", prior+", "+clientIP)
		} else {
			pr.Out.Header.Set("X-Forwarded-For", clientIP)
		}
	}

	// 透传 / 保证下游有 X-Request-Id（RequestLogger 已保证入站有值）
	if rid := pr.In.Header.Get("X-Request-Id"); rid != "" {
		pr.Out.Header.Set("X-Request-Id", rid)
	}
}

func pickTarget(c *gin.Context, b lb.Balancer) (string, bool) {
	key := c.ClientIP()
	if key == "" {
		key = c.Request.URL.Path
	}
	return b.NextKey(key)
}
