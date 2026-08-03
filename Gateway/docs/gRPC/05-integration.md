# ⑤ 与 HTTP 网关能力集成（3~5 天）

> 所属：[gRPC 目录](./README.md) · 前置：[④ grpc-proxy 网关](./04-grpc-proxy-gateway.md)  
> 本模块产出：配置扩展、`internal/grpcproxy/auth.go`、Registry 联动、（可选）限流与统计

---

## 模块目标

将 gRPC 网关与现有 HTTP 网关的**核心能力对齐**：JWT 租户鉴权、负载均衡、动态服务注册、（进阶）Redis 限流与流量统计。

**结束时应达到**：

- 无 Token 或非法 Token 时 gRPC 返回 `Unauthenticated`
- 合法 Token（与 HTTP 同一 `gateway/login` 签发）可经网关访问下游
- yaml 可配置 gRPC 监听地址与 upstream 列表
- （可选）动态注册 gRPC 节点并刷新 LB

---

## 进度对照

| 天 | 任务 | 状态 |
|----|------|------|
| 第 1 天 | `gateway.yaml` 增加 grpc 段 | ⬜ |
| 第 2 天 | metadata JWT 鉴权（复用 auth/tenant） | ⬜ |
| 第 3 天 | 复用 registry + 健康检查 | ⬜ |
| 第 4 天 | （可选）限流 interceptor | ⬜ |
| 第 5 天 | （可选）流量统计 + 与 HTTP 统一 tenant | ⬜ |

---

## 第 1 天：配置扩展

### 任务

1. 在 `internal/config/gateway.yaml` 增加 `grpc` 配置
2. `config.go` 增加解析与 Getter
3. `cmd/grpc-gateway` 从 `-config` 读取

### 推荐 yaml 片段

```yaml
# 现有 server.addr 仍为 HTTP :8080
server:
  addr: ":8080"

grpc:
  listen: ":50051"
  upstreams:
    - addr: "localhost:50052"
      weight: 1
    - addr: "localhost:50053"
      weight: 2
  # 与 HTTP 共用 jwt / tenants / ratelimit 段
  auth_required: true   # false 则跳过 metadata JWT（仅开发）
```

### Config 结构体扩展（参考）

```go
type GRPCConfig struct {
	Listen       string              `yaml:"listen"`
	Upstreams    []GRPCUpstreamConfig `yaml:"upstreams"`
	AuthRequired bool                `yaml:"auth_required"`
}

type GRPCUpstreamConfig struct {
	Addr   string `yaml:"addr"`
	Weight int    `yaml:"weight"`
}
```

### 初始化 LB

与 HTTP 共用 factory：

```go
nodes := make([]string, 0, len(cfg.GRPC.Upstreams))
for _, u := range cfg.GRPC.Upstreams {
	nodes = append(nodes, u.Addr)
}
balancer := lb.NewRoundRobin() // 或从 yaml strategy 读
balancer.SetNodes(nodes)
```

### 检验标准

- [ ] `-config internal/config/gateway.yaml` 可启动 gRPC 网关
- [ ] listen / upstreams 来自配置而非硬编码

---

## 第 2 天：JWT metadata 鉴权

### 任务

1. 新建 `internal/grpcproxy/auth.go`
2. 在 director **之前**用 Unary interceptor 校验（或对 streaming 用 Stream interceptor）
3. 复用 `auth.Parse` 与 `tenant.IsValid`

### HTTP 与 gRPC 鉴权对照

| 步骤 | HTTP (`middleware/auth.go`) | gRPC |
|------|----------------------------|------|
| 提取凭证 | `auth.ExtractBearer(c)` | metadata `authorization` |
| 解析 | `auth.Parse(token, secret)` | 相同 |
| 租户 | `tenant.IsValid(claims.Tenant)` | 相同 |
| 失败响应 | `401 JSON` | `codes.Unauthenticated` |
| 成功 | `c.Set("tenant", ...)` | `ctx` 存 tenant |

### 参考代码 — 从 metadata 提取 Bearer

```go
package grpcproxy

import (
	"context"
	"strings"

	"go-proxy-demo/internal/auth"
	"go-proxy-demo/internal/tenant"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
)

func AuthUnaryInterceptor(secret []byte, required bool) grpc.UnaryServerInterceptor {
	return func(ctx context.Context, req any, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (any, error) {
		newCtx, err := authenticate(ctx, secret, required)
		if err != nil {
			return nil, err
		}
		return handler(newCtx, req)
	}
}

func authenticate(ctx context.Context, secret []byte, required bool) (context.Context, error) {
	md, ok := metadata.FromIncomingContext(ctx)
	if !ok || len(md.Get("authorization")) == 0 {
		if required {
			return ctx, status.Error(codes.Unauthenticated, "missing token")
		}
		return ctx, nil
	}
	raw := md.Get("authorization")[0]
	tokenStr, ok := parseBearer(raw)
	if !ok {
		return ctx, status.Error(codes.Unauthenticated, "invalid authorization format")
	}
	claims, err := auth.Parse(tokenStr, secret)
	if err != nil {
		return ctx, status.Error(codes.Unauthenticated, "invalid token")
	}
	if !tenant.IsValid(claims.Tenant) {
		return ctx, status.Error(codes.Unauthenticated, "unknown tenant")
	}
	// 将 tenant 写入 outgoing，便于统计 interceptor 读取
	ctx = metadata.AppendToOutgoingContext(ctx, "x-tenant", claims.Tenant)
	return ctx, nil
}

func parseBearer(v string) (string, bool) {
	const p = "Bearer "
	if !strings.HasPrefix(v, p) {
		return "", false
	}
	return strings.TrimSpace(v[len(p):]), true
}
```

### 透明代理场景的拦截器挂载

`UnknownServiceHandler` 仍会走 `grpc.Server` 的 interceptor 链：

```go
s := grpc.NewServer(
	grpc.ChainUnaryInterceptor(
		grpcproxy.AuthUnaryInterceptor(cfg.JWTSecret(), cfg.GRPCAuthRequired()),
		grpcproxy.LoggingUnaryInterceptor(),
	),
	grpc.UnknownServiceHandler(proxy.TransparentHandler(director)),
)
```

### 获取 Token（与 HTTP 共用）

```powershell
# 登录
$r = curl.exe -s -X POST http://localhost:8080/gateway/login `
  -H "Content-Type: application/json" `
  -d '{"tenant":"tenant-a"}'
# 从 JSON 取 token 字段

go run ./cmd/grpc-client --gateway localhost:50051 --token "<JWT>" --name auth-test
```

### 检验标准

- [ ] 无 token + `auth_required: true` → RPC 失败
- [ ] 合法 tenant-a token → 成功
- [ ] 伪造 token → `Unauthenticated`

---

## 第 3 天：服务注册与健康检查

### 任务

1. Registry 存 gRPC 地址（`localhost:50052` 而非 `http://...`）
2. 健康检查：TCP dial 或 gRPC health
3. `registry.RefreshBalancer` 同步到 gRPC 所用 Balancer

### Registry 扩展思路

**方案 A — 复用现有 Registry，URL 存 host:port**

```go
reg.Register("localhost:50054")
registry.RefreshBalancer(reg, grpcBalancer)
```

**方案 B — 独立 GrpcRegistry 接口**（节点多时再拆）

### TCP 健康检查（简单）

HTTP 健康检查用 `GET /health`；gRPC 下游若无 health 服务，可在 `healthcheck.go` 增加：

```go
func tcpHealthy(addr string, timeout time.Duration) bool {
	conn, err := net.DialTimeout("tcp", addr, timeout)
	if err != nil {
		return false
	}
	_ = conn.Close()
	return true
}
```

### 管理 API 扩展（可选）

在 HTTP `handler/services.go` 增加 query 区分协议：

```powershell
# 注册 gRPC 节点（需实现 API）
curl.exe -X POST http://localhost:8080/gateway/services `
  -H "Content-Type: application/json" `
  -d '{"url":"localhost:50054","protocol":"grpc"}'
```

初期可在文档中约定：**gRPC 节点与 HTTP 分开管理**，或手工改 yaml 重启。

### 检验标准

- [ ] 不健康节点不会被 `balancer.Next()` 选中
- [ ] 动态 Register 后无需重启网关（若已实现 API）

---

## 第 4 天：（可选）限流

### 背景

Gin 中间件无法直接套在 gRPC 上，需 **interceptor** 或 director 内调用同一套限流逻辑。

### 复用 Redis 令牌桶

```go
func RateLimitUnaryInterceptor(rdb *redis.Client, rate, cap, daily int64) grpc.UnaryServerInterceptor {
	return func(ctx context.Context, req any, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (any, error) {
		tenantID := tenantFromContext(ctx)
		if tenantID == "" {
			tenantID = "anonymous"
		}
		if !ratelimit.Allow(ctx, rdb, tenantID, rate, cap, daily) {
			return nil, status.Error(codes.ResourceExhausted, "rate limit exceeded")
		}
		return handler(ctx, req)
	}
}
```

| gRPC code | 对应 HTTP |
|-----------|-----------|
| `ResourceExhausted` | 429 Too Many Requests |

### 检验标准

- [ ] 超限后 RPC 返回 ResourceExhausted
- [ ] 与 HTTP 共用 Redis key 规则（如 `ratelimit:{tenant}`）

---

## 第 5 天：（可选）流量统计

### 思路

在 Auth 之后、director 之前计数：

```go
key := fmt.Sprintf("stats:grpc:%s:%s", tenant, fullMethodName)
redis.Incr(ctx, key)
```

HTTP 已有 `middleware/statistic.go`，可抽公共函数 `stats.Record(tenant, path)`。

### 查询

扩展 `GET /gateway/statistic` 支持 `protocol=grpc`，或单独 `GET /gateway/statistic/grpc`。

---

## 与 HTTP 网关合并进程（可选架构）

### 双 Listener 同一进程

```go
// cmd/gateway/main.go 伪代码
go func() {
	grpcGateway.Run(cfg) // :50051
}()
r.Run(cfg.Server.Addr)  // :8080
```

| 优点 | 缺点 |
|------|------|
| 一份配置、一个进程 | main 变复杂 |
| 共享 Redis、Registry | 故障域不隔离 |

学习阶段建议 **`cmd/grpc-gateway` 独立**，稳定后再合并。

---

## 模块结束标准

- [ ] yaml 配置 gRPC listen / upstreams
- [ ] metadata JWT 鉴权与 HTTP 租户一致
- [ ] LB 与（可选）Registry 联动
- [ ] （加分）限流或统计任一落地

**下一步** → [06-testing-and-faq.md](./06-testing-and-faq.md)：完整验收与排错。
