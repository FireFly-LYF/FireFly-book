# ① 负载均衡（3~5 天）

> 所属：[阶段 3 目录](./README.md) · [phase3-readme-features.md](../phase3-readme-features.md)  
> 前置：完成 [阶段 2](../phase2-proxy-middleware.md)（单下游转发能跑通）  
> 起点：[`go-proxy-demo/`](../../go-proxy-demo/)  
> 本模块产出：`gateway/lb/` 包 + 多实例下游 + proxy 接入 Balancer

---

## 模块目标

把阶段 2「写死 `http://localhost:9001`」改成**从节点列表里选一个**再转发：

```
curl → 网关 :8080 → Balancer.Next() → 9001 / 9002 / 9003 之一
```

**结束时应达到**：3 个下游同时运行，连续请求经网关能命中不同 `instance` 字段。

---

## 进度对照

| 天 | 任务 | 状态 |
|----|------|------|
| 第 1 天 | 3 个下游实例 + `--port` + 响应带 `instance` | ⬜ |
| 第 2 天 | `Balancer` 接口 + 随机算法 | ⬜ |
| 第 3 天 | 轮询（Round Robin） | ⬜ |
| 第 4 天 | `proxy.Handler` 接入 Balancer | ⬜ |
| 第 5 天 | 自测 + 可选加权轮询 | ⬜ |

---

## 第 1 天：多下游实例

### 任务

1. 给 `cmd/downstream/main.go` 加 `--port` 命令行参数
2. 所有 JSON 响应的 `data` 里加 `"instance": "<port>"`
3. 同时启动 9001、9002、9003 三个进程

### 概念

| 概念 | 含义 |
|------|------|
| **实例** | 同一个程序的不同进程，监听不同端口 |
| **`--port`** | 命令行 flag，启动时指定端口 |
| **为什么 3 个** | 负载均衡至少 2 个才有意义，3 个更容易观察轮换 |

### 参考代码 — `cmd/downstream/main.go`

```go
package main

import (
    "flag"
    "log"

    "go-proxy-demo/downstream/handler"
    "github.com/gin-gonic/gin"
)

func main() {
    port := flag.String("port", "9001", "listen port")
    flag.Parse()

    handler.Instance = *port // 包级变量，handler 里读取

    r := gin.Default()
    r.GET("/user", handler.User)
    r.GET("/order", handler.Order)
    r.GET("/health", handler.Health)

    addr := ":" + *port
    log.Printf("downstream listening on %s (instance=%s)", addr, *port)
    if err := r.Run(addr); err != nil {
        log.Fatal(err)
    }
}
```

### 参考代码 — `downstream/handler/api.go` 改动

```go
package handler

import "github.com/gin-gonic/gin"

var Instance = "9001" // main 启动时覆盖

func User(c *gin.Context) {
    c.JSON(200, gin.H{
        "code": 0, "msg": "ok",
        "data": gin.H{"name": "张三", "role": "user", "instance": Instance},
    })
}
// Order、Health 同理加上 "instance": Instance
```

### 命令

```powershell
cd d:\A_Software\Java\SAVE\Gateway\go-proxy-demo

# 开 3 个终端，各跑一条
go run ./cmd/downstream --port 9001
go run ./cmd/downstream --port 9002
go run ./cmd/downstream --port 9003
```

### 验证

```powershell
curl.exe http://localhost:9001/user
curl.exe http://localhost:9002/user
curl.exe http://localhost:9003/user
# 三个响应的 data.instance 应分别为 9001 / 9002 / 9003
```

### 检验标准

- [ ] 3 个端口互不冲突，各自返回不同 `instance`
- [ ] `/health` 在三个实例上都可用（后续健康检查会用到）
- [ ] 能解释：为什么需要 3 个终端而不是 1 个程序监听 3 个端口

---

## 第 2 天：Balancer 接口 + 随机

### 任务

1. 新建 `gateway/lb/balancer.go` 定义接口
2. 新建 `gateway/lb/random.go` 实现随机选节点
3. 写单元测试或临时 main 打印 20 次 `Next()` 结果

### 概念

| 概念 | 含义 |
|------|------|
| **接口** | `Balancer` 抽象「选节点」，proxy 只依赖接口 |
| **随机** | `rand.Intn(len(nodes))`，实现简单，短期可能不均匀 |
| **空列表** | `Next()` 返回 `("", false)`，proxy 应返回 503 |

### 参考代码 — `gateway/lb/balancer.go`

```go
package lb

// Balancer 从节点列表中选出一个转发目标
type Balancer interface {
    SetNodes(nodes []string)
    Next() (target string, ok bool)
}
```

### 参考代码 — `gateway/lb/random.go`

```go
package lb

import (
    "math/rand"
    "sync"
)

type Random struct {
    mu    sync.RWMutex
    nodes []string
}

func NewRandom() *Random {
    return &Random{}
}

func (r *Random) SetNodes(nodes []string) {
    r.mu.Lock()
    defer r.mu.Unlock()
    r.nodes = append([]string(nil), nodes...)
}

func (r *Random) Next() (string, bool) {
    r.mu.RLock()
    defer r.mu.RUnlock()
    n := len(r.nodes)
    if n == 0 {
        return "", false
    }
    return r.nodes[rand.Intn(n)], true
}
```

### 临时验证（可选小测试）

```go
// gateway/lb/random_test.go
func TestRandomDistributes(t *testing.T) {
    b := NewRandom()
    b.SetNodes([]string{"http://localhost:9001", "http://localhost:9002", "http://localhost:9003"})
    hits := map[string]int{}
    for i := 0; i < 300; i++ {
        target, ok := b.Next()
        if !ok {
            t.Fatal("expected node")
        }
        hits[target]++
    }
    for _, n := range []string{"9001", "9002", "9003"} {
        if hits["http://localhost:"+n] == 0 {
            t.Fatalf("never hit %s", n)
        }
    }
}
```

### 检验标准

- [ ] `Balancer` 接口只有两个方法，职责清晰
- [ ] 空节点列表时 `Next()` 返回 `false`
- [ ] 能说出「为什么用接口而不是直接在 proxy 里写 if/else」

---

## 第 3 天：轮询 Round Robin

### 任务

1. 新建 `gateway/lb/roundrobin.go`
2. 用 `atomic.Uint64` 保证并发安全
3. 对比随机 vs 轮询：各发 9 次，观察顺序

### 概念

| 概念 | 含义 |
|------|------|
| **轮询** | 按 9001→9002→9003→9001… 依次选 |
| **`atomic.Uint64`** | 多 goroutine 同时请求时计数器仍正确 |
| **取模** | `idx % len(nodes)` 回到列表开头 |

### 参考代码 — `gateway/lb/roundrobin.go`

```go
package lb

import (
    "sync"
    "sync/atomic"
)

type RoundRobin struct {
    mu    sync.RWMutex
    nodes []string
    idx   atomic.Uint64
}

func NewRoundRobin() *RoundRobin {
    return &RoundRobin{}
}

func (r *RoundRobin) SetNodes(nodes []string) {
    r.mu.Lock()
    defer r.mu.Unlock()
    r.nodes = append([]string(nil), nodes...)
}

func (r *RoundRobin) Next() (string, bool) {
    r.mu.RLock()
    n := len(r.nodes)
    if n == 0 {
        r.mu.RUnlock()
        return "", false
    }
    nodes := r.nodes
    r.mu.RUnlock()

    i := r.idx.Add(1) - 1
    return nodes[i%uint64(n)], true
}
```

### 对比实验

| 算法 | 连续 6 次请求 instance 顺序（理想） |
|------|-------------------------------------|
| 轮询 | 9001, 9002, 9003, 9001, 9002, 9003 |
| 随机 | 不固定，但长期比例接近 |

### 检验标准

- [ ] 轮询在单线程下严格按顺序
- [ ] 能解释 `atomic` 和 `mutex` 各自保护什么
- [ ] README 里四种 LB，本阶段先掌握随机 + 轮询

---

## 第 4 天：proxy 接入 Balancer

### 任务

1. 修改 `gateway/proxy/reverse.go`：`Handler(b lb.Balancer)` 替代 `Handler(target string)`
2. 每次请求 `balancer.Next()` 得到 target，动态创建或缓存 ReverseProxy
3. 无可用节点时返回 503 JSON

### 概念

| 概念 | 含义 |
|------|------|
| **动态 target** | 不再写死 URL，每次请求可能不同 |
| **Proxy 缓存** | 可按 target 缓存 `*httputil.ReverseProxy`，避免重复创建 |
| **503** | 没有健康节点时的标准响应 |

### 参考代码 — `gateway/proxy/reverse.go`

```go
package proxy

import (
    "net/http"
    "net/http/httputil"
    "net/url"
    "strings"
    "sync"

    "go-proxy-demo/gateway/lb"
    "github.com/gin-gonic/gin"
)

func Handler(b lb.Balancer) gin.HandlerFunc {
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
                pr.SetURL(remote)
                pr.Out.URL.Path = strings.TrimPrefix(pr.In.URL.Path, "/api")
                if pr.Out.URL.Path == "" {
                    pr.Out.URL.Path = "/"
                }
            },
        }
        cache[target] = p
        return p, nil
    }

    return func(c *gin.Context) {
        target, ok := b.Next()
        if !ok {
            c.JSON(503, gin.H{"code": 503, "msg": "no upstream available"})
            return
        }
        p, err := getProxy(target)
        if err != nil {
            c.JSON(502, gin.H{"code": 502, "msg": "bad upstream"})
            return
        }
        p.ServeHTTP(c.Writer, c.Request)
    }
}
```

### 参考代码 — `cmd/gateway/main.go`

```go
balancer := lb.NewRoundRobin()
balancer.SetNodes([]string{
    "http://localhost:9001",
    "http://localhost:9002",
    "http://localhost:9003",
})

r.Any("/api/*path", proxy.Handler(balancer))
```

### 命令

```powershell
# 终端 1~3：下游
go run ./cmd/downstream --port 9001
go run ./cmd/downstream --port 9002
go run ./cmd/downstream --port 9003

# 终端 4：网关
go run ./cmd/gateway
```

### 验证

```powershell
1..9 | ForEach-Object {
    curl.exe -s http://localhost:8080/api/user | Select-String "instance"
}
```

轮询模式下应看到 `9001` → `9002` → `9003` 循环。

### 检验标准

- [ ] 网关不再硬编码单个 target
- [ ] 停掉一个下游后，请求仍打到剩余实例（不会 502 整个网关）
- [ ] 停掉全部下游后，返回 503
- [ ] `proxy` 包 import 的是 `lb.Balancer` 接口

---

## 第 5 天：自测 + 可选进阶

### 任务

1. 完成模块毕业自测
2. （可选）实现加权轮询 `gateway/lb/weighted.go`
3. 整理目录，确认 `main.go` 只组装 Balancer

### 毕业自测

```powershell
# 1. 三下游 + 网关
# 2. 轮询 9 次
1..9 | ForEach-Object { curl.exe -s http://localhost:8080/api/user }

# 3. 停 9002，再发 6 次 — 只应出现 9001、9003
# 4. 全停 — 503
curl.exe http://localhost:8080/api/user
```

### 可选：加权轮询思路

```go
// 节点带权重：9001:1, 9002:2, 9003:3
// 展开为 ["9001","9002","9002","9003","9003","9003"] 再轮询
// 或 smooth weighted round robin（README 进阶）
```

### 模块结束标准

- [ ] 能同时启动 3 下游 + 1 网关
- [ ] 轮询命中不同 `instance`
- [ ] 能画「Balancer.Next → ReverseProxy → 下游」流程图
- [ ] 能解释 README 里「四种负载均衡」已掌握哪两种

**全部打勾 → 进入 [② JWT 鉴权](./02-jwt-auth.md)**

---

## 常见问题

### Q：随机和轮询选哪个作为默认？

学习阶段用**轮询**，结果可预测、好 debug；生产可配置切换。

### Q：每个请求都 `url.Parse` 会不会慢？

用 map 缓存 `ReverseProxy`（第 4 天代码已体现）。

### Q：一致性 Hash 什么时候学？

HTTP 网关核心跑通后再做；需要「同一用户总打同一节点」时用（如 sticky session）。
