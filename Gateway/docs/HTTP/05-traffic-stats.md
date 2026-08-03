# ⑤ 流量统计（3~5 天）

> 所属：[阶段 3 目录](./README.md) · 前置：[③ Redis 限流](./03-redis-ratelimit.md)（复用 Redis 连接）  
> 本模块产出：`gateway/stats/` + `gateway/middleware/stats.go` + 查询 API

---

## 模块目标

每次**成功转发**后，按租户（+ 可选路径）在 Redis 累加计数；提供管理 API 查询：

```
转发成功 → INCR stats:tenant-a:20260704
GET /gateway/stats?tenant=tenant-a → {"count": 42}
```

---

## 进度对照

| 天 | 任务 | 状态 |
|----|------|------|
| 第 1 天 | Redis 计数 key 设计 + 基础 INCR | ⬜ |
| 第 2 天 | Stats 中间件（转发后记录） | ⬜ |
| 第 3 天 | `GET /gateway/stats` 查询 API | ⬜ |
| 第 4 天 | 按路径细分统计（可选） | ⬜ |
| 第 5 天 | 简单报表 JSON（可选） | ⬜ |

---

## 第 1 天：Redis 计数 key 设计

### 任务

1. 设计 key 规范
2. 新建 `gateway/stats/counter.go`：`Incr` / `Get`
3. redis-cli 手动验证

### Key 规范

```
stats:tenant-a:20260704              # 租户日总量
stats:tenant-a:20260704:/api/user    # 按路径（第 4 天）
stats:global:20260704                # 全站（可选）
```

### 参考代码 — `gateway/stats/counter.go`

```go
package stats

import (
    "context"
    "fmt"
    "time"

    "github.com/redis/go-redis/v9"
)

func dayKey(tenant string, day time.Time) string {
    return fmt.Sprintf("stats:%s:%s", tenant, day.Format("20060102"))
}

func Incr(ctx context.Context, rdb *redis.Client, tenant string) error {
    key := dayKey(tenant, time.Now())
    pipe := rdb.Pipeline()
    pipe.Incr(ctx, key)
    pipe.Expire(ctx, key, 90*24*time.Hour) // 保留约 90 天
    _, err := pipe.Exec(ctx)
    return err
}

func Get(ctx context.Context, rdb *redis.Client, tenant string, day time.Time) (int64, error) {
    key := dayKey(tenant, day)
    n, err := rdb.Get(ctx, key).Int64()
    if err == redis.Nil {
        return 0, nil
    }
    return n, err
}
```

### 手动验证

```powershell
docker exec -it redis redis-cli
INCR stats:tenant-a:20260704
INCR stats:tenant-a:20260704
GET stats:tenant-a:20260704
```

### 检验标准

- [ ] key 含租户 + 日期，便于按天查
- [ ] GET 不存在的 key 返回 0 而不是报错
- [ ] 设置了合理 EXPIRE

---

## 第 2 天：Stats 中间件

### 任务

1. 新建 `gateway/middleware/stats.go`
2. 在 `c.Next()` **之后**根据状态码决定是否计数
3. 统计失败只打日志，**不影响**响应

### 概念

| 点 | 说明 |
|----|------|
| **记录时机** | 转发完成后，已知 status code |
| **只计成功** | 通常 `2xx` 才 INCR（429/401 不计） |
| **fail silent** | Redis 错误不返回 500 给客户端 |

### 参考代码

```go
package middleware

import (
    "context"
    "log"

    "go-proxy-demo/gateway/stats"
    "github.com/gin-gonic/gin"
    "github.com/redis/go-redis/v9"
)

func TrafficStats(rdb *redis.Client) gin.HandlerFunc {
    return func(c *gin.Context) {
        c.Next()

        if c.Writer.Status() < 200 || c.Writer.Status() >= 300 {
            return
        }
        tenantVal, ok := c.Get("tenant")
        if !ok {
            return
        }
        tenant, _ := tenantVal.(string)
        if err := stats.Incr(context.Background(), rdb, tenant); err != nil {
            log.Printf("stats incr failed: %v", err)
        }
    }
}
```

### 挂载位置

Stats 应在 proxy **同一路由组内**，且在 proxy handler **之前注册**（Gin 中先注册的先进入，但 `c.Next()` 后执行顺序相反）：

```go
api := r.Group("/api")
api.Use(middleware.JWTAuth(...))
api.Use(middleware.RateLimit(...))
api.Use(middleware.TrafficStats(rdb)) // 在 proxy 前注册，Next 后在 proxy 之后执行
api.Any("/*path", proxy.Handler(balancer))
```

### 洋葱模型

```
进入 Stats → 进入 Proxy → 下游响应
← Stats 记录计数 ← Proxy 返回
```

### 验证

```powershell
# 发 5 次成功请求
1..5 | ForEach-Object {curl.exe -s -H "Authorization: Bearer $token" http://localhost:8080/api/user}

docker exec -it redis redis-cli GET stats:tenant-a:20260704
# 应为 5（或接查询 API）
```

### 检验标准

- [ ] 401 / 429 请求不计入统计
- [ ] Redis 宕机时客户端仍得 200
- [ ] 能解释为什么放在 proxy 前注册、却在 proxy 后计数

---

## 第 3 天：查询 API

### 任务

1. `GET /gateway/statistic?tenant=tenant-a`
2. 可选 `&date=20260704`，默认今天
3. 返回 `{code, msg, data: {tenant, date, count}}`

### 参考代码

```go
func (a *Admin) GetStats(c *gin.Context) {
    tenant := c.Query("tenant")
    if tenant == "" {
        c.JSON(400, gin.H{"code": 400, "msg": "tenant required"})
        return
    }
    dateStr := c.DefaultQuery("date", time.Now().Format("20060102"))
    day, err := time.Parse("20060102", dateStr)
    if err != nil {
        c.JSON(400, gin.H{"code": 400, "msg": "bad date"})
        return
    }
    count, err := stats.Get(c.Request.Context(), a.RDB, tenant, day)
    if err != nil {
        c.JSON(500, gin.H{"code": 500, "msg": "redis error"})
        return
    }
    c.JSON(200, gin.H{
        "code": 0, "msg": "ok",
        "data": gin.H{"tenant": tenant, "date": dateStr, "count": count},
    })
}
```

### 验证

```powershell
curl.exe "http://localhost:8080/gateway/stats?tenant=tenant-a"
curl.exe "http://localhost:8080/gateway/stats?tenant=tenant-a&date=20260704"
```

### 检验标准

- [ ] 查询结果与 redis-cli GET 一致
- [ ] tenant-a / tenant-b 数据隔离
- [ ] 无 tenant 参数返回 400

---

## 第 4 天：按路径细分（可选）

### 任务

1. `IncrPath(ctx, tenant, path)`
2. key：`stats:tenant-a:20260704:/api/user`
3. 查询 API 加 `&path=/api/user`

### 参考代码

```go
func IncrPath(ctx context.Context, rdb *redis.Client, tenant, path string) error {
    key := fmt.Sprintf("stats:%s:%s:%s", tenant, time.Now().Format("20060102"), path)
    pipe := rdb.Pipeline()
    pipe.Incr(ctx, key)
    pipe.Incr(ctx, dayKey(tenant, time.Now())) // 同时累加日总量
    pipe.Exec(ctx)
    return nil
}
```

### 检验标准

- [ ] 总请求数 = 各路径之和（若 double-count 需在文档说明策略）
- [ ] README「流量统计」按租户维度已满足

---

## 第 5 天：简单报表（可选）

### 任务

1. `GET /gateway/stats/report?tenant=tenant-a` 返回最近 7 天数组
2. 用 `MGET` 或循环 `Get` 批量读

### 参考响应

```json
{
  "code": 0,
  "data": {
    "tenant": "tenant-a",
    "days": [
      {"date": "20260628", "count": 10},
      {"date": "20260629", "count": 25}
    ]
  }
}
```

### 模块结束标准

- [ ] 成功转发后 Redis 计数增加
- [ ] 查询 API 可用
- [ ] 统计不阻塞主链路
- [ ] 能说明与限流 Redis key 的命名区分

**全部打勾 → 进入 [⑥ 中间件链](./06-middleware-chain.md)**

---

## 常见问题

### Q：Stats 和 RateLimit 都用 INCR，会混吗？

key 前缀不同：`ratelimit:*` vs `stats:*`。

### Q：要不要统计响应时间？

README 大盘在阶段 4；本阶段只做请求量。耗时已有 RequestLogger。

### Q：异步写 Redis 更好吗？

高 QPS 时可 channel + 批量写；学习阶段同步 INCR 即可。
