# ④ 服务注册（3~5 天）

> 所属：[阶段 3 目录](./README.md) · 前置：[① 负载均衡](./01-load-balancer.md)（LB 已接入 proxy）  
> 本模块产出：`gateway/registry/memory.go` + 管理 API + 健康检查

---

## 模块目标

运行时动态增删下游节点，并自动更新 Balancer；不健康节点不参与转发：

```
POST /gateway/services {"url":"http://localhost:9004"}  → 注册
DELETE /gateway/services {"url":"..."}                  → 下线
后台每 10s 探测 /health                                 → 失败摘除
```

---

## 进度对照

| 天 | 任务 | 状态 |
|----|------|------|
| 第 1 天 | `Registry` 接口定义 | ⬜ |
| 第 2 天 | 内存实现 + 联动 Balancer | ⬜ |
| 第 3 天 | 注册 / 下线 HTTP API | ⬜ |
| 第 4 天 | 定时健康检查 | ⬜ |
| 第 5 天 | 动态加节点端到端自测 | ⬜ |

---

## 第 1 天：Registry 接口

### 任务

1. 定义 `gateway/registry/registry.go` 接口
2. 明确节点结构：URL、是否健康、最后检查时间

### 概念

| 概念 | 含义 |
|------|------|
| **注册中心** | 存「有哪些下游可以转发」 |
| **内存版** | map + mutex，重启丢失（阶段 4 可换 MySQL） |
| **健康状态** | 探测失败标记 unhealthy，LB 跳过 |

### 参考代码 — `gateway/registry/registry.go`

```go
package registry

import "time"

type Node struct {
    URL       string    `json:"url"`
    Healthy   bool      `json:"healthy"`
    LastCheck time.Time `json:"last_check"`
}

type Registry interface {
    Register(url string) error
    Deregister(url string) error
    List() []Node
    ListHealthyURLs() []string
    SetHealth(url string, healthy bool)
}
```

### 检验标准

- [ ] 接口方法职责清晰
- [ ] `ListHealthyURLs()` 供 Balancer 直接使用
- [ ] 能解释与 ① 里硬编码节点列表的区别

---

## 第 2 天：内存实现 + 联动 LB

### 任务

1. 实现 `gateway/registry/memory.go`
2. 封装 `SyncBalancer(reg Registry, b lb.Balancer)`：注册变化时 `SetNodes`
3. 启动时从 registry 初始化 9001~9003

### 参考代码 — `gateway/registry/memory.go`

```go
package registry

import (
    "errors"
    "sync"
    "time"
)

var ErrDuplicate = errors.New("node already registered")
var ErrNotFound = errors.New("node not found")

type Memory struct {
    mu    sync.RWMutex
    nodes map[string]*Node
}

func NewMemory(initial []string) *Memory {
    m := &Memory{nodes: make(map[string]*Node)}
    for _, u := range initial {
        _ = m.Register(u)
    }
    return m
}

func (m *Memory) Register(url string) error {
    m.mu.Lock()
    defer m.mu.Unlock()
    if _, ok := m.nodes[url]; ok {
        return ErrDuplicate
    }
    m.nodes[url] = &Node{URL: url, Healthy: true, LastCheck: time.Now()}
    return nil
}

func (m *Memory) Deregister(url string) error {
    m.mu.Lock()
    defer m.mu.Unlock()
    if _, ok := m.nodes[url]; !ok {
        return ErrNotFound
    }
    delete(m.nodes, url)
    return nil
}

func (m *Memory) List() []Node {
    m.mu.RLock()
    defer m.mu.RUnlock()
    out := make([]Node, 0, len(m.nodes))
    for _, n := range m.nodes {
        out = append(out, *n)
    }
    return out
}

func (m *Memory) ListHealthyURLs() []string {
    m.mu.RLock()
    defer m.mu.RUnlock()
    var urls []string
    for _, n := range m.nodes {
        if n.Healthy {
            urls = append(urls, n.URL)
        }
    }
    return urls
}

func (m *Memory) SetHealth(url string, healthy bool) {
    m.mu.Lock()
    defer m.mu.Unlock()
    if n, ok := m.nodes[url]; ok {
        n.Healthy = healthy
        n.LastCheck = time.Now()
    }
}
```

### 联动 Balancer

```go
func RefreshBalancer(reg registry.Registry, b lb.Balancer) {
    b.SetNodes(reg.ListHealthyURLs())
}
```

### 检验标准

- [ ] Register 重复 URL 返回 ErrDuplicate
- [ ] Deregister 不存在的 URL 返回 ErrNotFound
- [ ] SetHealth(false) 后 ListHealthyURLs 不含该节点

---

## 第 3 天：管理 API

### 任务

1. `GET /gateway/services` — 列出所有节点
2. `POST /gateway/services` — 注册
3. `DELETE /gateway/services` — 下线
4. 每次变更后 `RefreshBalancer`

### 参考代码 — `gateway/handler/admin.go`

```go
package handler

import (
    "go-proxy-demo/gateway/lb"
    "go-proxy-demo/gateway/registry"
    "github.com/gin-gonic/gin"
)

type Admin struct {
    Reg registry.Registry
    LB  lb.Balancer
}

func (a *Admin) ListServices(c *gin.Context) {
    c.JSON(200, gin.H{"code": 0, "msg": "ok", "data": a.Reg.List()})
}

func (a *Admin) RegisterService(c *gin.Context) {
    var req struct{ URL string `json:"url" binding:"required"` }
    if err := c.ShouldBindJSON(&req); err != nil {
        c.JSON(400, gin.H{"code": 400, "msg": "bad request"})
        return
    }
    if err := a.Reg.Register(req.URL); err != nil {
        c.JSON(409, gin.H{"code": 409, "msg": err.Error()})
        return
    }
    registry.RefreshBalancer(a.Reg, a.LB)
    c.JSON(200, gin.H{"code": 0, "msg": "ok"})
}

func (a *Admin) DeregisterService(c *gin.Context) {
    var req struct{ URL string `json:"url" binding:"required"` }
    if err := c.ShouldBindJSON(&req); err != nil {
        c.JSON(400, gin.H{"code": 400, "msg": "bad request"})
        return
    }
    if err := a.Reg.Deregister(req.URL); err != nil {
        c.JSON(404, gin.H{"code": 404, "msg": err.Error()})
        return
    }
    registry.RefreshBalancer(a.Reg, a.LB)
    c.JSON(200, gin.H{"code": 0, "msg": "ok"})
}
```

### 路由注册

```go
admin := &handler.Admin{Reg: reg, LB: balancer}
r.GET("/gateway/services", admin.ListServices)
r.POST("/gateway/services", admin.RegisterService)
r.DELETE("/gateway/services", admin.DeregisterService)
```

### 验证

```powershell
curl.exe http://localhost:8080/gateway/services

curl.exe -X POST http://localhost:8080/gateway/services `
  -H "Content-Type: application/json" `
  -d "{\"url\":\"http://localhost:9004\"}"

curl.exe -X DELETE http://localhost:8080/gateway/services `
  -H "Content-Type: application/json" `
  -d "{\"url\":\"http://localhost:9004\"}"
```

### 检验标准

- [ ] 列表 API 返回 url / healthy / last_check
- [ ] 注册后 Balancer 立即包含新 URL
- [ ] 下线后不再转发到该 URL

---

## 第 4 天：健康检查

### 任务

1. 新建 `gateway/registry/healthcheck.go`
2. 每 10 秒对所有节点 `GET {url}/health`
3. 超时 2 秒、非 200 视为 unhealthy；恢复后设回 healthy

### 参考代码

```go
package registry

import (
    "context"
    "log"
    "net/http"
    "time"
)

func StartHealthCheck(reg Registry, b interface{ SetNodes([]string) }, interval time.Duration) {
    client := &http.Client{Timeout: 2 * time.Second}
    go func() {
        ticker := time.NewTicker(interval)
        defer ticker.Stop()
        for range ticker.C {
            for _, node := range reg.List() {
                healthy := probe(client, node.URL+"/health")
                reg.SetHealth(node.URL, healthy)
                if !healthy {
                    log.Printf("health check failed: %s", node.URL)
                }
            }
            b.SetNodes(reg.ListHealthyURLs())
        }
    }()
}

func probe(client *http.Client, url string) bool {
    req, _ := http.NewRequestWithContext(context.Background(), http.MethodGet, url, nil)
    resp, err := client.Do(req)
    if err != nil {
        return false
    }
    defer resp.Body.Close()
    return resp.StatusCode == http.StatusOK
}
```

### 启动

```go
registry.StartHealthCheck(reg, balancer, 10*time.Second)
```

### 验证

```powershell
# 停掉 9002 进程，等 ~10 秒
curl.exe http://localhost:8080/gateway/services
# 9002 healthy 应变为 false

# 连续请求，不应再命中 instance=9002
1..9 | ForEach-Object { curl.exe -s -H "Authorization: Bearer $token" http://localhost:8080/api/user }
```

### 检验标准

- [ ] 下游挂掉后约一个周期内被摘除
- [ ] 重启下游后自动恢复 healthy
- [ ] 健康检查在独立 goroutine，不阻塞请求

---

## 第 5 天：端到端自测

### 任务

1. 网关运行中启动 9004 并注册
2. 确认请求能命中 9004
3. 下线 9004，确认不再命中

### 完整流程

```powershell
# 1. 网关 + 9001~9003 已运行
# 2. 启动 9004
go run ./cmd/downstream --port 9004

# 3. 注册
curl.exe -X POST http://localhost:8080/gateway/services `
  -H "Content-Type: application/json" `
  -d "{\"url\":\"http://localhost:9004\"}"

# 4. 带 Token 轮询，应出现 instance=9004
1..12 | ForEach-Object {
    curl.exe -s -H "Authorization: Bearer $token" http://localhost:8080/api/user
}

# 5. 下线 9004
curl.exe -X DELETE http://localhost:8080/gateway/services `
  -H "Content-Type: application/json" `
  -d "{\"url\":\"http://localhost:9004\"}"
```

### 模块结束标准

- [ ] 动态注册 / 下线 API 可用
- [ ] 健康检查自动摘除 / 恢复
- [ ] Balancer 与 Registry 始终同步
- [ ] 能解释 README「动态服务注册」在网关里的作用

**全部打勾 → 进入 [⑤ 流量统计](./05-traffic-stats.md)**

---

## 常见问题

### Q：注册 API 要不要鉴权？

学习阶段 `/gateway/*` 暂不鉴权；阶段 4 Admin 加管理员 Token。

### Q：和 Nacos / Consul 区别？

原理相同；本阶段内存版理解「注册 → 发现 → LB」链路即可。

### Q：健康检查路径必须是 /health 吗？

与下游约定一致；阶段 2 已预留 `/health`。
