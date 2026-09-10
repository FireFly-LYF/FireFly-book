// Package config 负责读取 gateway.yaml，校验并初始化运行时依赖（租户、JWT、负载均衡器）。
package config

import (
	"fmt"
	"os"
	"strings"
	"time"

	"gateway/internal/lb"
	"gateway/internal/tenant"
	"gateway/internal/upstream"

	"github.com/goccy/go-yaml"
)

// Config 网关全局配置。
// 带 yaml tag 的字段从 gateway.yaml 反序列化；小写字段在 Load 时计算，仅通过 Getter 暴露。
type Config struct {
	Server         ServerConfig         `yaml:"server"`
	GRPC           GRPCConfig           `yaml:"grpc"`
	TCP            TCPConfig            `yaml:"tcp"`
	Security       SecurityConfig       `yaml:"security"`
	CORS           CORSConfig           `yaml:"cors"`
	JWT            JWTConfig            `yaml:"jwt"`
	Admin          AdminConfig          `yaml:"admin"`
	InternalAuth   InternalAuthConfig   `yaml:"internal_auth"`
	Tenants        []string             `yaml:"tenants"`
	Redis          RedisConfig          `yaml:"redis"`
	RateLimit      RateLimitConfig      `yaml:"ratelimit"`
	CircuitBreaker CircuitBreakerConfig `yaml:"circuit_breaker"`
	Registry       RegistryConfig       `yaml:"registry"`
	LoadBalancer   LBConfig             `yaml:"loadbalancer"`
	Proxy          ProxyConfig          `yaml:"proxy"`
	Routes         []RouteConfig        `yaml:"routes"`

	jwtSecret      []byte        // 业务 Access JWT 密钥
	adminJWTSecret []byte        // 运维 /gateway/* JWT 密钥（必须与业务不同）
	internalHMAC   []byte        // 下游身份头 HMAC 密钥
	tokenTTL       time.Duration // JWT.TokenTTL 解析后的时长，如 "24h"
	adminTokenTTL  time.Duration // Admin.TokenTTL
	lbCfg          lb.Config     // HTTP 整理后的 LB 配置（节点 URL + 权重）
	balancer       lb.Balancer   // HTTP 负载均衡器，proxy 直接调用
	grpcBalancer   lb.Balancer   // gRPC 负载均衡器，grpc director 调用
}

// ProxyConfig 控制反向代理路径改写与上游超时。
// StripPrefix 非空时剥离此前缀（旧演示：/api）；空则保留完整路径（FireFly Java：/api/user/...）。
type ProxyConfig struct {
	StripPrefix string `yaml:"strip_prefix"`
	// ResponseHeaderTimeoutSec 等待上游响应头的秒数；超时记熔断失败。<=0 时用默认 10。
	ResponseHeaderTimeoutSec int `yaml:"response_header_timeout_sec"`
}

// RouteConfig 按路径前缀转发到独立 upstream 组。
type RouteConfig struct {
	ID        string           `yaml:"id"`
	Prefix    string           `yaml:"prefix"`
	Auth      *bool            `yaml:"auth"` // nil 表示跟随 jwt.api_required
	Upstreams []UpstreamConfig `yaml:"upstreams"`
}

type RedisConfig struct {
	Addr string `yaml:"addr"` // Redis 地址，如 localhost:6379
}

type RateLimitConfig struct {
	// Enabled 为 false 时完全跳过限流中间件（对比实验用）；缺省/true 保持开启
	Enabled    *bool  `yaml:"enabled"`
	Rate       int64  `yaml:"rate"`        // 默认：每秒补充令牌数（平均 QPS）
	Capacity   int64  `yaml:"capacity"`    // 默认：令牌桶容量（突发上限）
	DailyLimit int64  `yaml:"daily_limit"` // 每个限流键每日上限（QPD），0 表示不限制
	// KeyBy 限流键：user_ip（登录用户按 userId，否则按 IP）| ip | tenant
	KeyBy  string                 `yaml:"key_by"`
	Routes []RateLimitRouteConfig `yaml:"routes"` // 按路径前缀覆盖配额；最长前缀优先
}

// RateLimitRouteConfig 单条路径的 QPS/突发配额；未写的字段回落到全局 rate/capacity。
type RateLimitRouteConfig struct {
	Prefix   string `yaml:"prefix"`
	Rate     int64  `yaml:"rate"`
	Capacity int64  `yaml:"capacity"`
}

type ServerConfig struct {
	Addr string `yaml:"addr"` // 监听地址，如 :8080
}

type JWTConfig struct {
	Secret      string `yaml:"secret"`       // HS256 签名密钥
	TokenTTL    string `yaml:"token_ttl"`    // Token 有效期，Go duration 格式，如 24h、30m
	APIRequired *bool  `yaml:"api_required"` // /api 是否强制 JWT；nil/true=强制，false=开发联调可跳过
}

// AdminConfig 网关运维通道：与业务 JWT 完全隔离。
type AdminConfig struct {
	JWTSecret string `yaml:"jwt_secret"` // 仅签发/校验 /gateway/* Token
	Password  string `yaml:"password"`   // POST /gateway/login 口令
	TokenTTL  string `yaml:"token_ttl"`  // 运维 Token 有效期；空则 8h
}

// InternalAuthConfig 网关 → Java 下游的轻量身份证明（HMAC，非用户 JWT）。
type InternalAuthConfig struct {
	HMACSecret string `yaml:"hmac_secret"` // 与各服务 firefly.internal-auth.hmac-secret 一致
}

type LBConfig struct {
	Strategy  string           `yaml:"strategy"`  // roundrobin | random | weighted | consistent_hash
	Upstreams []UpstreamConfig `yaml:"upstreams"` // 下游节点列表
}

type UpstreamConfig struct {
	HTTP   string `yaml:"http"`   // HTTP base URL，如 http://localhost:9001
	GRPC   string `yaml:"grpc"`   // gRPC 地址，如 localhost:50052
	TCP    string `yaml:"tcp"`    // TCP 地址，如 localhost:6379
	Addr   string `yaml:"addr"`   // HTTP / gRPC / TCP 同 host:port
	Weight int    `yaml:"weight"` // 仅 weighted 策略使用；<=0 时按 1 处理
}

type SecurityConfig struct {
	IPBlocklist []string `yaml:"ip_blocklist"` // IP 黑名单
}

// CORSConfig 浏览器跨域白名单。未命中的 Origin 不写 Allow-Origin。
type CORSConfig struct {
	AllowedOrigins   []string `yaml:"allowed_origins"`
	AllowCredentials bool     `yaml:"allow_credentials"` // 仅白名单命中时写入
}

type CircuitBreakerConfig struct {
	Threshold   int `yaml:"threshold"`    // 连续 5xx 次数达到后开路；0 表示不启用
	CooldownSec int `yaml:"cooldown_sec"` // 开路持续时间（秒）
}

type GRPCConfig struct {
	Listen       string `yaml:"listen"`
	AuthRequired bool   `yaml:"auth_required"`
}

type TCPConfig struct {
	Listen string `yaml:"listen"` // 为空时不启动 TCP 代理
}

type RegistryConfig struct {
	Type string `yaml:"type"` // memory | mysql
	DSN  string `yaml:"dsn"`  // MySQL 连接串
}

// Load 读取 yaml 并完成校验与运行时初始化，是 config 包的唯一入口。
// 流程：默认值 → 展开 ${ENV} → yaml 覆盖 → validate → initRuntime
// 密钥必须由环境变量注入；未设置时 ExpandEnv 为空，validate 会失败。
func Load(path string) (*Config, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("read config %q: %w", path, err)
	}
	data = []byte(os.ExpandEnv(string(data)))

	cfg := defaultConfig()
	if err := yaml.Unmarshal(data, cfg); err != nil {
		return nil, fmt.Errorf("parse config %q: %w", path, err)
	}
	if err := cfg.validate(); err != nil {
		return nil, err
	}
	if err := cfg.initRuntime(); err != nil {
		return nil, err
	}
	return cfg, nil
}

// initRuntime 将 yaml 中的原始值转为各模块可直接使用的运行时对象。
func (c *Config) initRuntime() error {
	// 初始化租户白名单，供 login 与 JWTAuth 校验 iss
	tenant.Configure(c.Tenants)

	ttl, err := c.parseTokenTTL()
	if err != nil {
		return fmt.Errorf("jwt.token_ttl: %w", err)
	}
	c.tokenTTL = ttl
	c.jwtSecret = []byte(c.JWT.Secret)
	c.internalHMAC = []byte(c.InternalAuth.HMACSecret)
	c.adminJWTSecret = []byte(c.Admin.JWTSecret)
	adminTTL, err := c.parseAdminTokenTTL()
	if err != nil {
		return fmt.Errorf("admin.token_ttl: %w", err)
	}
	c.adminTokenTTL = adminTTL

	lbCfg, err := c.BalancerConfig()
	if err != nil {
		return err
	}
	c.lbCfg = lbCfg

	balancer, err := lb.NewBalancer(lbCfg)
	if err != nil {
		return fmt.Errorf("loadbalancer: %w", err)
	}
	c.balancer = balancer

	if c.GRPCEnabled() {
		grpcLB, err := c.GRPCBalancerConfig()
		if err != nil {
			return err
		}
		grpcBalancer, err := lb.NewBalancer(grpcLB)
		if err != nil {
			return fmt.Errorf("grpc upstreams: %w", err)
		}
		c.grpcBalancer = grpcBalancer
	}
	return nil
}

func (c *Config) Balancer() lb.Balancer {
	return c.balancer
}

func (c *Config) LBConfig() lb.Config {
	return c.lbCfg
}

// defaultConfig 提供 yaml 缺字段时的兜底值；yaml 中显式配置会覆盖这些默认值。
func defaultConfig() *Config {
	return &Config{
		Server: ServerConfig{Addr: ":8080"},
		GRPC: GRPCConfig{
			Listen:       ":50051",
			AuthRequired: true,
		},
		JWT: JWTConfig{
			Secret:   "", // 必须由 ${JWT_SECRET} 注入
			TokenTTL: "24h",
		},
		Admin: AdminConfig{
			JWTSecret: "", // ${ADMIN_JWT_SECRET}
			Password:  "", // ${ADMIN_PASSWORD}
			TokenTTL:  "8h",
		},
		InternalAuth: InternalAuthConfig{
			HMACSecret: "", // ${INTERNAL_HMAC_SECRET}
		},
		Tenants: []string{"tenant-a", "tenant-b"},
		CORS: CORSConfig{
			AllowedOrigins: []string{
				"http://localhost:5173",
				"http://127.0.0.1:5173",
			},
			AllowCredentials: true,
		},
		Redis: RedisConfig{Addr: "localhost:6379"},
		RateLimit: RateLimitConfig{
			Rate:       30,
			Capacity:   60,
			DailyLimit: 20000,
			KeyBy:      "user_ip",
		},
		CircuitBreaker: CircuitBreakerConfig{
			Threshold:   5,
			CooldownSec: 30,
		},
		Proxy: ProxyConfig{
			ResponseHeaderTimeoutSec: 10,
		},
		Registry: RegistryConfig{Type: "memory"},
		LoadBalancer: LBConfig{
			Strategy: "consistent_hash",
			Upstreams: []UpstreamConfig{
				{HTTP: "http://localhost:9001", Weight: 1},
				{HTTP: "http://localhost:9002", Weight: 2},
				{HTTP: "http://localhost:9003", Weight: 3},
			},
		},
	}
}

// validate 检查启动必需的配置项，Load 时 yaml 解析后立即执行。
func (c *Config) validate() error {
	if c.Server.Addr == "" {
		return fmt.Errorf("server.addr must not be empty")
	}
	if c.JWT.Secret == "" {
		return fmt.Errorf("jwt.secret must not be empty")
	}
	if c.InternalAuth.HMACSecret == "" {
		return fmt.Errorf("internal_auth.hmac_secret must not be empty")
	}
	if c.Admin.JWTSecret == "" {
		return fmt.Errorf("admin.jwt_secret must not be empty")
	}
	if c.Admin.Password == "" {
		return fmt.Errorf("admin.password must not be empty")
	}
	if c.Admin.JWTSecret == c.JWT.Secret {
		return fmt.Errorf("admin.jwt_secret must differ from jwt.secret")
	}
	if _, err := c.parseTokenTTL(); err != nil {
		return fmt.Errorf("jwt.token_ttl: %w", err)
	}
	if _, err := c.parseAdminTokenTTL(); err != nil {
		return fmt.Errorf("admin.token_ttl: %w", err)
	}
	if len(c.Tenants) == 0 {
		return fmt.Errorf("tenants must not be empty")
	}
	if len(c.Routes) == 0 && len(c.LoadBalancer.Upstreams) == 0 {
		return fmt.Errorf("routes or loadbalancer.upstreams must not be empty")
	}
	for i, u := range c.LoadBalancer.Upstreams {
		if u.HTTP == "" && u.GRPC == "" && u.TCP == "" && u.Addr == "" {
			return fmt.Errorf("loadbalancer.upstreams[%d] requires http, grpc, tcp, or addr", i)
		}
	}
	seenID := map[string]struct{}{}
	seenPrefix := map[string]struct{}{}
	for i, route := range c.Routes {
		if route.ID == "" {
			return fmt.Errorf("routes[%d].id must not be empty", i)
		}
		if route.Prefix == "" {
			return fmt.Errorf("routes[%d].prefix must not be empty", i)
		}
		if len(route.Upstreams) == 0 {
			return fmt.Errorf("routes[%d].upstreams must not be empty", i)
		}
		if _, ok := seenID[route.ID]; ok {
			return fmt.Errorf("duplicate routes id %q", route.ID)
		}
		if _, ok := seenPrefix[route.Prefix]; ok {
			return fmt.Errorf("duplicate routes prefix %q", route.Prefix)
		}
		seenID[route.ID] = struct{}{}
		seenPrefix[route.Prefix] = struct{}{}
		for j, u := range route.Upstreams {
			if u.HTTP == "" && u.Addr == "" {
				return fmt.Errorf("routes[%d].upstreams[%d] requires http or addr", i, j)
			}
		}
	}
	if c.GRPCEnabled() {
		var hasGRPC bool
		for _, ep := range c.Upstreams() {
			if _, ok := ep.ResolvedGRPC(); ok {
				hasGRPC = true
				break
			}
		}
		if !hasGRPC {
			return fmt.Errorf("grpc.listen is set but no grpc upstream in loadbalancer.upstreams")
		}
	}
	if c.TCPEnabled() {
		var hasTCP bool
		for _, ep := range c.Upstreams() {
			if _, ok := ep.ResolvedTCP(); ok {
				hasTCP = true
				break
			}
		}
		if !hasTCP {
			return fmt.Errorf("tcp.listen is set but no tcp upstream in loadbalancer.upstreams")
		}
	}
	if _, err := c.LBStrategy(); err != nil {
		return err
	}
	if c.RegistryType() == "mysql" && c.RegistryDSN() == "" {
		return fmt.Errorf("registry.dsn must not be empty when registry.type=mysql")
	}
	return nil
}

func (c *Config) JWTSecret() []byte {
	return c.jwtSecret
}

// AdminJWTSecret 运维 /gateway/* 专用密钥；业务 Access 无法通过此密钥验签。
func (c *Config) AdminJWTSecret() []byte {
	return c.adminJWTSecret
}

// AdminPassword 运维登录口令。
func (c *Config) AdminPassword() string {
	return c.Admin.Password
}

// AdminTokenTTLDuration 运维 Token TTL。
func (c *Config) AdminTokenTTLDuration() time.Duration {
	return c.adminTokenTTL
}

func (c *Config) parseAdminTokenTTL() (time.Duration, error) {
	if c.Admin.TokenTTL == "" {
		return 8 * time.Hour, nil
	}
	return time.ParseDuration(c.Admin.TokenTTL)
}

// InternalHMACSecret 网关注入 X-Gateway-Sign 所用密钥。
func (c *Config) InternalHMACSecret() []byte {
	return c.internalHMAC
}

// APIAuthRequired /api 业务链是否强制 JWT；未配置时默认 true（兼容旧行为）。
func (c *Config) APIAuthRequired() bool {
	if c.JWT.APIRequired == nil {
		return true
	}
	return *c.JWT.APIRequired
}

// StripPrefix 代理路径剥离前缀；空表示保留完整路径。
func (c *Config) StripPrefix() string {
	return c.Proxy.StripPrefix
}

// ProxyResponseHeaderTimeout 等待上游响应头的超时；默认 10s。
func (c *Config) ProxyResponseHeaderTimeout() time.Duration {
	sec := c.Proxy.ResponseHeaderTimeoutSec
	if sec <= 0 {
		sec = 10
	}
	return time.Duration(sec) * time.Second
}

// HasHTTPRoutes 是否启用按路径路由表。
func (c *Config) HasHTTPRoutes() bool {
	return len(c.Routes) > 0
}

// RouteAuthRequired 单条路由是否鉴权；未设置时跟随全局 api_required。
func (c *Config) RouteAuthRequired(route RouteConfig) bool {
	if route.Auth != nil {
		return *route.Auth
	}
	return c.APIAuthRequired()
}

// RouteUpstreams 把某条 route 的 upstreams 规范为 Endpoint。
func (c *Config) RouteUpstreams(route RouteConfig) []upstream.Endpoint {
	eps := make([]upstream.Endpoint, 0, len(route.Upstreams))
	for _, u := range route.Upstreams {
		ep, err := upstream.Normalize(u.HTTP, u.GRPC, u.TCP, u.Addr, u.Weight)
		if err != nil {
			continue
		}
		eps = append(eps, ep)
	}
	return eps
}

// RedisAddr 返回 Redis 地址；yaml 未配置时默认 localhost:6379。
func (c *Config) RedisAddr() string {
	if c.Redis.Addr == "" {
		return "localhost:6379"
	}
	return c.Redis.Addr
}

// RateLimitEnabled 是否挂载限流中间件；yaml ratelimit.enabled=false 时关闭。
func (c *Config) RateLimitEnabled() bool {
	if c.RateLimit.Enabled != nil {
		return *c.RateLimit.Enabled
	}
	return true
}

// RateLimitRate 返回 QPS 补充速率；<=0 时默认 5。
func (c *Config) RateLimitRate() int64 {
	if c.RateLimit.Rate <= 0 {
		return 5
	}
	return c.RateLimit.Rate
}

// RateLimitCapacity 返回令牌桶容量；未配置或小于 rate 时与 rate 对齐。
func (c *Config) RateLimitCapacity() int64 {
	rate := c.RateLimitRate()
	if c.RateLimit.Capacity <= 0 {
		return rate
	}
	if c.RateLimit.Capacity < rate {
		return rate
	}
	return c.RateLimit.Capacity
}

// RateLimitDailyLimit 返回日配额上限；0 表示 middleware 不启用 QPD 检查。
func (c *Config) RateLimitDailyLimit() int64 {
	return c.RateLimit.DailyLimit
}

// RateLimitKeyBy 限流键策略；空或未知时按 user_ip。
func (c *Config) RateLimitKeyBy() string {
	switch strings.ToLower(strings.TrimSpace(c.RateLimit.KeyBy)) {
	case "ip":
		return "ip"
	case "tenant":
		return "tenant"
	default:
		return "user_ip"
	}
}

// RateLimitRouteQuotas 路由级配额（已规范化 rate/capacity）；最长前缀匹配由 Limiter 负责。
func (c *Config) RateLimitRouteQuotas() []RateLimitRouteConfig {
	out := make([]RateLimitRouteConfig, 0, len(c.RateLimit.Routes))
	defRate := c.RateLimitRate()
	for _, r := range c.RateLimit.Routes {
		prefix := strings.TrimSpace(r.Prefix)
		if prefix == "" {
			continue
		}
		rate := r.Rate
		if rate <= 0 {
			rate = defRate
		}
		cap := r.Capacity
		if cap <= 0 {
			cap = rate
		}
		if cap < rate {
			cap = rate
		}
		out = append(out, RateLimitRouteConfig{Prefix: prefix, Rate: rate, Capacity: cap})
	}
	return out
}

// CBThreshold 返回熔断连续失败阈值；<=0 时默认 5，且 middleware 可据此跳过挂载。
func (c *Config) CBThreshold() int {
	if c.CircuitBreaker.Threshold <= 0 {
		return 5
	}
	return c.CircuitBreaker.Threshold
}

// CBCooldown 返回熔断开路冷却时间；<=0 时默认 30s。
func (c *Config) CBCooldown() time.Duration {
	sec := c.CircuitBreaker.CooldownSec
	if sec <= 0 {
		return 30 * time.Second
	}
	return time.Duration(sec) * time.Second
}

// CircuitBreakerEnabled 是否启用熔断；yaml 中 threshold=0 显式关闭。
func (c *Config) CircuitBreakerEnabled() bool {
	return c.CircuitBreaker.Threshold != 0
}

func (c *Config) TokenTTL() (time.Duration, error) {
	return c.tokenTTL, nil
}

// TokenTTLDuration 返回 Token 有效期，login 签发时使用。
func (c *Config) TokenTTLDuration() time.Duration {
	return c.tokenTTL
}

// parseTokenTTL 将 yaml 中的 "24h" 等字符串解析为 time.Duration。
func (c *Config) parseTokenTTL() (time.Duration, error) {
	if c.JWT.TokenTTL == "" {
		return 24 * time.Hour, nil
	}
	return time.ParseDuration(c.JWT.TokenTTL)
}

// LBStrategy 解析负载均衡策略名，支持 roundrobin / consistent_hash 等别名。
func (c *Config) LBStrategy() (lb.Strategy, error) {
	return lb.ParseStrategy(c.LoadBalancer.Strategy)
}

// Upstreams 把 yaml 中的 loadbalancer.upstreams 规范为统一 Endpoint 列表。
func (c *Config) Upstreams() []upstream.Endpoint {
	eps := make([]upstream.Endpoint, 0, len(c.LoadBalancer.Upstreams))
	for _, u := range c.LoadBalancer.Upstreams {
		ep, err := upstream.Normalize(u.HTTP, u.GRPC, u.TCP, u.Addr, u.Weight)
		if err != nil {
			continue
		}
		eps = append(eps, ep)
	}
	return eps
}

// BalancerConfig 把 yaml 中的 HTTP 下游转为 lb 包需要的 Config 结构。
func (c *Config) BalancerConfig() (lb.Config, error) {
	strategy, err := c.LBStrategy()
	if err != nil {
		return lb.Config{}, err
	}

	var nodes []string
	var weights []int
	for _, ep := range c.Upstreams() {
		u, ok := ep.ResolvedHTTP()
		if !ok {
			continue
		}
		nodes = append(nodes, u)
		w := ep.Weight
		if w <= 0 {
			w = 1
		}
		weights = append(weights, w)
	}

	return lb.Config{
		Strategy: strategy,
		Nodes:    nodes,
		Weights:  weights,
	}, nil
}

// GRPCEnabled 是否启动 gRPC 监听；grpc.listen 为空时关闭 gRPC 入口。
func (c *Config) GRPCEnabled() bool {
	return c.GRPC.Listen != ""
}

// GRPCListen 返回 gRPC 监听地址，如 :50051。
func (c *Config) GRPCListen() string {
	return c.GRPC.Listen
}

// GRPCAuthRequired 是否强制 metadata JWT 鉴权。
func (c *Config) GRPCAuthRequired() bool {
	return c.GRPC.AuthRequired
}

// GRPCBalancer 返回 gRPC 下游负载均衡器；仅在 GRPCEnabled 时非 nil。
func (c *Config) GRPCBalancer() lb.Balancer {
	return c.grpcBalancer
}

// GRPCUpstreamAddrs 返回 yaml 中配置的 gRPC 下游地址列表（用于启动日志）。
func (c *Config) GRPCUpstreamAddrs() []string {
	var addrs []string
	for _, ep := range c.Upstreams() {
		if a, ok := ep.ResolvedGRPC(); ok {
			addrs = append(addrs, a)
		}
	}
	return addrs
}

// TCPEnabled 是否启动 TCP 监听；tcp.listen 为空时关闭 TCP 入口。
func (c *Config) TCPEnabled() bool {
	return c.TCP.Listen != ""
}

// TCPListen 返回 TCP 监听地址，如 :8081。
func (c *Config) TCPListen() string {
	return c.TCP.Listen
}

// RegistryType 返回 registry 存储类型：memory（默认）或 mysql。
func (c *Config) RegistryType() string {
	if c.Registry.Type == "" {
		return "memory"
	}
	return c.Registry.Type
}

// RegistryDSN 返回 MySQL 连接串。
func (c *Config) RegistryDSN() string {
	return c.Registry.DSN
}

// GRPCBalancerConfig 把 gRPC 下游转为 lb 包需要的 Config；策略与 HTTP 共用 loadbalancer.strategy。
func (c *Config) GRPCBalancerConfig() (lb.Config, error) {
	strategy, err := c.LBStrategy()
	if err != nil {
		return lb.Config{}, err
	}

	var nodes []string
	var weights []int
	for _, ep := range c.Upstreams() {
		a, ok := ep.ResolvedGRPC()
		if !ok {
			continue
		}
		nodes = append(nodes, a)
		w := ep.Weight
		if w <= 0 {
			w = 1
		}
		weights = append(weights, w)
	}

	return lb.Config{
		Strategy: strategy,
		Nodes:    nodes,
		Weights:  weights,
	}, nil
}
