# ③ Redis 限流（1 周）

> 所属：[阶段 3 目录](./README.md) · 前置：[② JWT 鉴权](./02-jwt-auth.md)  
> 本模块产出：`gateway/redis/client.go` + `gateway/middleware/ratelimit.go`

---

## 模块目标

鉴权通过后、转发前，按**租户**限制 QPS，超限返回 **429**：

```
tenant-a 第 1~10 次/秒 → 200
tenant-a 第 11 次/秒   → 429
tenant-b 独立计数，互不影响
```

---

## 进度对照

| 天 | 任务 | 状态 |
|----|------|------|
| 第 1 天 | 安装 Redis + 基础命令 | ⬜ |
| 第 2 天 | 令牌桶 / 固定窗口概念 | ⬜ |
| 第 3 天 | Redis 固定窗口 QPS（入门实现） | ⬜ |
| 第 4 天 | `RateLimit` 中间件 + 429 | ⬜ |
| 第 5 天 | 令牌桶 Lua 脚本（贴近 README） | ⬜ |
| 第 6 天 | 按路径不同限额（可选） | ⬜ |
| 第 7 天 | QPD 每日配额（可选） | ⬜ |

---

## 第 1 天：安装 Redis + 基础命令

### 任务

1. 用 Docker 启动 Redis
2. 练习 `SET` / `GET` / `INCR` / `EXPIRE` / `TTL`
3. 新建 `gateway/redis/client.go` 连接封装

### Redis 启动

```powershell
docker run -d --name redis -p 6379:6379 redis:7-alpine
docker exec -it redis redis-cli ping
# PONG
```

若已有容器：`docker start redis`

### 必会命令

```powershell
docker exec -it redis redis-cli

127.0.0.1:6379> SET hello world
127.0.0.1:6379> GET hello
127.0.0.1:6379> INCR counter
127.0.0.1:6379> INCR counter
127.0.0.1:6379> EXPIRE counter 10
127.0.0.1:6379> TTL counter
```

| 命令 | 用途 |
|------|------|
| `INCR key` | 原子 +1，限流计数核心 |
| `EXPIRE key sec` |  key 过期自动删，防止内存泄漏 |
| `TTL key` | 查看剩余秒数 |

### 安装 Go Redis 客户端

```powershell
go get github.com/redis/go-redis/v9
```

### 参考代码 — `gateway/redis/client.go`

```go
package redisx

import (
    "context"
    "time"

    "github.com/redis/go-redis/v9"
)

func New(addr string) *redis.Client {
    return redis.NewClient(&redis.Options{
        Addr:         addr,
        DialTimeout:  3 * time.Second,
        ReadTimeout:  2 * time.Second,
        WriteTimeout: 2 * time.Second,
    })
}

func Ping(ctx context.Context, c *redis.Client) error {
    return c.Ping(ctx).Err()
}
```

### 验证

```go
// 临时写在 main 或 TestMain
client := redisx.New("localhost:6379")
if err := redisx.Ping(context.Background(), client); err != nil {
    log.Fatal(err)
}
```

### 检验标准

- [ ] `redis-cli ping` 返回 PONG
- [ ] Go 程序能连上 Redis
- [ ] 能解释 INCR 为什么适合并发计数

---

## 第 2 天：限流算法概念

### 任务

1. 理解固定窗口 vs 滑动窗口 vs 令牌桶
2. 对照 README：「令牌桶算法实现 QPS 与 QPD」
3. 设计 Redis key 命名规范

### 概念对比

| 算法 | 原理 | 优点 | 缺点 |
|------|------|------|------|
| **固定窗口** | 每秒一个 key，`INCR` 超 limit 拒绝 | 实现简单 | 窗口边界可能突发 2× |
| **滑动窗口** | 记录时间戳列表或分桶 | 更平滑 | 实现复杂 |
| **令牌桶** | 按速率放令牌，请求消耗令牌 | README 采用，允许合理突发 | 需 Lua 保证原子性 |

### Key 设计

```
ratelimit:qps:tenant-a:1704067200   # 固定窗口：租户 + 秒级时间戳
ratelimit:qpd:tenant-a:20260704     # 每日配额
ratelimit:bucket:tenant-a           # 令牌桶（第 5 天）
```

### 检验标准

- [ ] 能用自己的话解释三种算法
- [ ] 知道 README 目标是令牌桶 + QPD
- [ ] 第 3 天先用固定窗口入门，第 5 天升级令牌桶

---

## 第 3 天：固定窗口 QPS（入门）

### 任务

1. 新建 `gateway/ratelimit/fixed_window.go`
2. 函数 `Allow(ctx, client, tenant, limit int) (bool, error)`
3. 单元测试：同一秒内 INCR 超过 limit 返回 false

### 参考代码

```go
package ratelimit

import (
    "context"
    "fmt"
    "time"

    "github.com/redis/go-redis/v9"
)

func FixedWindowAllow(ctx context.Context, rdb *redis.Client, tenant string, limit int64) (bool, error) {
    sec := time.Now().Unix()
    key := fmt.Sprintf("ratelimit:qps:%s:%d", tenant, sec)

    n, err := rdb.Incr(ctx, key).Result()
    if err != nil {
        return false, err
    }
    if n == 1 {
        _ = rdb.Expire(ctx, key, 2*time.Second).Err()
    }
    return n <= limit, nil
}
```

### 手动验证

```powershell
# redis-cli 模拟
INCR ratelimit:qps:tenant-a:999
INCR ratelimit:qps:tenant-a:999
# ... 超过 10 即应拒绝
```

### 检验标准

- [ ] 同一秒内第 11 次（limit=10）返回 false
- [ ] 下一秒 key 变化，计数重置
- [ ] Redis 失败时决定策略：本阶段可「fail open」放行并打日志

---

## 第 4 天：RateLimit 中间件

### 任务

1. 新建 `gateway/middleware/ratelimit.go`
2. 从 `c.Get("tenant")` 取租户（依赖 ② JWT）
3. 超限 `429` + `c.Abort()`

### 参考代码

```go
package middleware

import (
    "context"
    "log"

    "go-proxy-demo/gateway/ratelimit"
    "github.com/gin-gonic/gin"
    "github.com/redis/go-redis/v9"
)

func RateLimit(rdb *redis.Client, qps int64) gin.HandlerFunc {
    return func(c *gin.Context) {
        tenantVal, exists := c.Get("tenant")
        if !exists {
            c.Next() // 非 /api 路由不应进此中间件
            return
        }
        tenant, _ := tenantVal.(string)

        ok, err := ratelimit.FixedWindowAllow(context.Background(), rdb, tenant, qps)
        if err != nil {
            log.Printf("ratelimit redis error: %v", err)
            c.Next() // fail open
            return
        }
        if !ok {
            c.JSON(429, gin.H{"code": 429, "msg": "rate limit exceeded"})
            c.Abort()
            return
        }
        c.Next()
    }
}
```

### 挂载顺序（重要）

```go
api := r.Group("/api")
api.Use(middleware.JWTAuth(...))      // 先鉴权，设置 tenant
api.Use(middleware.RateLimit(rdb, 5)) // 再限流
api.Any("/*path", proxy.Handler(balancer))
```

### 验证

```powershell
$token = (curl.exe -s -X POST http://localhost:8080/gateway/login `
  -H "Content-Type: application/json" `
  -d '{"tenant":"tenant-a"}' | ConvertFrom-Json).data.token

1..12 | ForEach-Object {
    curl.exe -s -o NUL -w "%{http_code}\n" `
      -H "Authorization: Bearer $token" `
      http://localhost:8080/api/user
}
# 期望：前 5 个 200，后面 429（limit=5 时）
```

### 检验标准

- [ ] 超限返回 429，不是 401
- [ ] 无 Token 的请求在 JWT 层已 401，不计入限流
- [ ] tenant-a 和 tenant-b 计数独立

---

## 第 5 天：令牌桶（贴近 README）

### 任务

1. 理解令牌桶：容量 `capacity`，每秒补充 `rate` 个令牌
2. 用 Redis + Lua 脚本保证「取令牌」原子性
3. 替换或并存固定窗口实现

### 参考 Lua 脚本（简化版）

```lua
-- KEYS[1] = bucket key
-- ARGV[1] = now_ms, ARGV[2] = rate, ARGV[3] = capacity
local data = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens = tonumber(data[1]) or tonumber(ARGV[3])
local ts = tonumber(data[2]) or tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local capacity = tonumber(ARGV[3])
local now = tonumber(ARGV[1])
local delta = math.max(0, now - ts) / 1000.0
tokens = math.min(capacity, tokens + delta * rate)
if tokens < 1 then
  return 0
end
tokens = tokens - 1
redis.call('HMSET', KEYS[1], 'tokens', tokens, 'ts', now)
redis.call('EXPIRE', KEYS[1], 60)
return 1
```

### Go 调用

```go
var tokenBucketScript = redis.NewScript(luaScript)

func TokenBucketAllow(ctx context.Context, rdb *redis.Client, tenant string, rate, capacity int64) (bool, error) {
    key := "ratelimit:bucket:" + tenant
    now := time.Now().UnixMilli()
    n, err := tokenBucketScript.Run(ctx, rdb, []string{key}, now, rate, capacity).Int()
    return n == 1, err
}
```

### 检验标准

- [ ] 能解释令牌桶如何允许「短时突发」
- [ ] 能说出为什么需要 Lua（读-改-写必须原子）
- [ ] 中间件可配置用固定窗口或令牌桶

---

## 第 6 天：按路径限额（可选）

### 任务

1. key 加路径：`ratelimit:qps:tenant-a:/api/order:timestamp`
2. 或维护 map：`/api/order` limit=3，`/api/user` limit=10
3. 敏感接口更严

### 参考思路

```go
func limitForPath(path string) int64 {
    switch {
    case strings.HasPrefix(path, "/api/order"):
        return 3
    default:
        return 10
    }
}
```

### 检验标准

- [ ] 不同路径可有不同 limit
- [ ] 路径取自 `c.Request.URL.Path`

---

## 第 7 天：QPD 每日配额（可选）

### 任务

1. key：`ratelimit:qpd:tenant-a:20260704`
2. 每天 INCR，超过 dailyLimit 返回 429
3. EXPIRE 设为 48 小时

### 参考代码

```go
func QPDAllow(ctx context.Context, rdb *redis.Client, tenant string, dailyLimit int64) (bool, error) {
    day := time.Now().Format("20060102")
    key := fmt.Sprintf("ratelimit:qpd:%s:%s", tenant, day)
    n, err := rdb.Incr(ctx, key).Result()
    if err != nil {
        return false, err
    }
    if n == 1 {
        _ = rdb.Expire(ctx, key, 48*time.Hour).Err()
    }
    return n <= dailyLimit, nil
}
```

### 中间件组合

```go
// 先检查 QPD，再检查 QPS
if !qpdOk { 429; Abort }
if !qpsOk { 429; Abort }
c.Next()
```

### 模块结束标准

- [ ] Redis 运行，Go 能连接
- [ ] 超限 429，正常 200
- [ ] 限流在 JWT 之后、proxy 之前
- [ ] 至少实现固定窗口；令牌桶 / QPD 为加分

**全部打勾 → 进入 [④ 服务注册](./04-service-registry.md)**

---

## 常见问题

### Q：Redis 挂了怎么办？

学习阶段 **fail open**（放行 + 日志）；生产可 fail closed 或降级本地计数。

### Q：固定窗口边界问题？

第 5 天令牌桶可缓解；或滑动窗口（进阶）。

### Q：限流响应要加 Header 吗？

建议加。429 响应附带 `Retry-After`、`X-RateLimit-Limit`、`X-RateLimit-Remaining`、`X-RateLimit-Reset`，便于客户端自动退避重试。
