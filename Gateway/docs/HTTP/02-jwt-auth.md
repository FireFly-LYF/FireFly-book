# ② JWT 鉴权（3~5 天）

> 所属：[阶段 3 目录](./README.md) · 前置：[① 负载均衡](./01-load-balancer.md)  
> 本模块产出：`gateway/middleware/auth.go` + `gateway/tenant/` + Token 签发测试接口

---

## 模块目标

在转发前校验 `Authorization: Bearer <JWT>`，提取 `iss`（租户）与内存租户列表匹配，失败返回 **401**：

```
curl（无 Token）→ 401
curl（合法 Token, iss=tenant-a）→ 200，转发下游
curl（/gateway/health）→ 200，不鉴权
```

---

## 进度对照

| 天 | 任务 | 状态 |
|----|------|------|
| 第 1 天 | JWT 三段结构 + jwt.io 解码 | ⬜ |
| 第 2 天 | 安装 jwt 库，写签发 / 解析 | ⬜ |
| 第 3 天 | 从 Header 提取 Bearer Token | ⬜ |
| 第 4 天 | `JWTAuth` 中间件 + 租户校验 | ⬜ |
| 第 5 天 | 白名单路由 + 测试登录接口 | ⬜ |

---

## 第 1 天：理解 JWT 结构

### 任务

1. 阅读 [jwt.io/introduction](https://jwt.io/introduction)
2. 在 jwt.io Debugger 粘贴示例 Token，观察 Header / Payload
3. 搞懂 `iss`（Issuer）、`exp`（过期时间）字段

### 概念

| 部分 | 内容 | 作用 |
|------|------|------|
| **Header** | `{"alg":"HS256","typ":"JWT"}` | 声明算法 |
| **Payload** | `{"iss":"tenant-a","exp":...}` | 业务数据（不加密，只签名） |
| **Signature** | 对前两段 HMAC | 防篡改 |

| 字段 | 含义 |
|------|------|
| `iss` | 签发者 = 租户 ID（README 用来匹配租户列表） |
| `exp` | Unix 时间戳，过期后 Token 无效 |
| `sub` | 主题，通常是用户 ID（本阶段可选） |

### 动手：解码示例

访问 jwt.io，Payload 示例：

```json
{
  "iss": "tenant-a",
  "sub": "user-001",
  "exp": 1893456000
}
```

### 检验标准

- [ ] 能说出 JWT 三段各干什么
- [ ] 知道 Payload 是 Base64 可读，**不是加密**
- [ ] 知道 README 用 `iss` 做租户匹配

---

## 第 2 天：签发与解析

### 任务

1. `go get github.com/golang-jwt/jwt/v5`
2. 新建 `gateway/auth/token.go`：`Sign` / `Parse`
3. 写 `token_test.go` 验证 round-trip

### 安装

```powershell
cd d:\A_Software\Java\SAVE\Gateway\go-proxy-demo
go get github.com/golang-jwt/jwt/v5
```

### 参考代码 — `gateway/auth/token.go`

```go
package auth

import (
    "time"

    "github.com/golang-jwt/jwt/v5"
)

var DefaultSecret = []byte("phase3-dev-secret-change-me")


type Claims struct {
    Tenant string `json:"iss"`
    jwt.RegisteredClaims
}

func Sign(tenant string, ttl time.Duration) (string, error) {
    claims := Claims{
        Tenant: tenant,
        RegisteredClaims: jwt.RegisteredClaims{
            Issuer:    tenant,
            ExpiresAt: jwt.NewNumericDate(time.Now().Add(ttl)),
            IssuedAt:  jwt.NewNumericDate(time.Now()),
        },
    }
    token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
    return token.SignedString(DefaultSecret)
}

func Parse(tokenStr string, secret []byte) (*Claims, error) {
    token, err := jwt.ParseWithClaims(tokenStr, &Claims{}, func(t *jwt.Token) (any, error) {
        return secret, nil
    })
    if err != nil {
        return nil, err
    }
    claims, ok := token.Claims.(*Claims)
    if !ok || !token.Valid {
        return nil, jwt.ErrTokenInvalidClaims
    }
    return claims, nil
}
```

### 测试

```go
func TestSignParse(t *testing.T) {
    s, err := auth.Sign("tenant-a", time.Hour)
    if err != nil {
        t.Fatal(err)
    }
    c, err := auth.Parse(s, auth.DefaultSecret)
    if err != nil || c.Tenant != "tenant-a" {
        t.Fatalf("got %v %v", c, err)
    }
}
```

### 检验标准

- [ ] `Sign` 能生成字符串，`Parse` 能还原 `tenant-a`
- [ ] 改过一位字符后 `Parse` 失败
- [ ] 过期 Token 解析失败（可把 ttl 设为 `-1s` 测试）

---

## 第 3 天：Bearer Header 提取

### 任务

1. 理解 HTTP Header：`Authorization: Bearer eyJhbG...`
2. 写 `ExtractBearer(c *gin.Context) (string, bool)`
3. 用临时路由测试提取逻辑

### 概念

| 情况 | 处理 |
|------|------|
| 无 Header | 401 missing token |
| `Basic xxx` | 401（不是 Bearer） |
| `Bearer` 后无内容 | 401 |
| `Bearer <token>` | 正常提取 |

### 参考代码 — `gateway/auth/header.go`

```go
package auth

import (
    "strings"

    "github.com/gin-gonic/gin"
)

func ExtractBearer(c *gin.Context) (string, bool) {
    h := c.GetHeader("Authorization")
    if !strings.HasPrefix(h, "Bearer ") {
        return "", false
    }
    token := strings.TrimSpace(strings.TrimPrefix(h, "Bearer "))
    return token, token != ""
}
```

### 临时测试路由

```go
r.GET("/debug/token", func(c *gin.Context) {
    if t, ok := auth.ExtractBearer(c); ok {
        c.JSON(200, gin.H{"token_prefix": t[:min(20, len(t))]})
        return
    }
    c.JSON(401, gin.H{"msg": "no bearer"})
})
```

### 验证

```powershell
curl.exe http://localhost:8080/debug/token
curl.exe -H "Authorization: Bearer abc" http://localhost:8080/debug/token
```

### 检验标准

- [ ] 正确识别 Bearer 前缀（大小写敏感，标准写法是 `Bearer`）
- [ ] 空 Token 被拒绝

---

## 第 4 天：JWTAuth 中间件

### 任务

1. 新建 `gateway/tenant/store.go` 内存租户表
2. 新建 `gateway/middleware/auth.go`
3. 挂到 `/api` 路由组

### 参考代码 — `gateway/tenant/store.go`

```go
package tenant

func IsValid(issuer string) bool {
    _, ok := validIssuers[issuer]
    return ok
}

var validIssuers = map[string]bool{
    "tenant-a": true,
    "tenant-b": true,
}
```

### 参考代码 — `gateway/middleware/auth.go`

```go
package middleware

import (
    "go-proxy-demo/gateway/auth"
    "go-proxy-demo/gateway/tenant"
    "github.com/gin-gonic/gin"
)

func JWTAuth(secret []byte) gin.HandlerFunc {
    return func(c *gin.Context) {
        tokenStr, ok := auth.ExtractBearer(c)
        if !ok {
            c.JSON(401, gin.H{"code": 401, "msg": "missing token"})
            c.Abort()
            return
        }
        claims, err := auth.Parse(tokenStr, secret)
        if err != nil {
            c.JSON(401, gin.H{"code": 401, "msg": "invalid token"})
            c.Abort()
            return
        }
        if !tenant.IsValid(claims.Tenant) {
            c.JSON(401, gin.H{"code": 401, "msg": "unknown tenant"})
            c.Abort()
            return
        }
        c.Set("tenant", claims.Tenant)
        c.Next()
    }
}
```

### 挂载方式

```go
api := r.Group("/api")
api.Use(middleware.JWTAuth(auth.DefaultSecret))
api.Any("/*path", proxy.Handler(balancer))
```

### 验证

```powershell
# 无 Token
curl.exe -w "\n%{http_code}\n" http://localhost:8080/api/user

# 合法 Token（先用下面登录接口拿）
curl.exe -H "Authorization: Bearer <token>" http://localhost:8080/api/user

# 伪造 iss
# 用 jwt.io 改 iss 为 tenant-x，应 401 unknown tenant
```

### 检验标准

- [ ] 三种 401 场景：missing / invalid / unknown tenant
- [ ] 通过后 `c.Get("tenant")` 可取到值（供限流、统计用）
- [ ] 鉴权失败走 `c.Abort()`，不进入 proxy

---

## 第 5 天：白名单 + 登录接口

### 任务

1. `/gateway/*` 管理接口不挂 JWT 中间件
2. 加 `POST /gateway/login` 签发 Token（学习用，生产应接真实用户库）
3. 整理：删除 `/debug/token` 临时路由

### 参考代码 — 登录接口

```go
r.POST("/gateway/login", func(c *gin.Context) {
    var req struct {
        Tenant string `json:"tenant"`
    }
    if err := c.BindJSON(&req); err != nil || !tenant.IsValid(req.Tenant) {
        c.JSON(400, gin.H{"code": 400, "msg": "bad tenant"})
        return
    }
    token, err := auth.Sign(req.Tenant, 24*time.Hour)
    if err != nil {
        c.JSON(500, gin.H{"code": 500, "msg": "sign failed"})
        return
    }
    c.JSON(200, gin.H{"code": 0, "msg": "ok", "data": gin.H{"token": token}})
})
```

### 完整验证流程

```powershell
# 1. 登录拿 Token
curl.exe -X POST http://localhost:8080/gateway/login `
  -H "Content-Type: application/json" `
  -d "{\"tenant\":\"tenant-a\"}"

# 2. 复制 token 访问 API
curl.exe -H "Authorization: Bearer <token>" http://localhost:8080/api/user

# 3. 白名单
curl.exe http://localhost:8080/gateway/health
```

### 路由分层示意

```
/gateway/health     → 无鉴权
/gateway/login      → 无鉴权
/gateway/services   → 无鉴权（④ 模块加）
/api/*              → JWTAuth → proxy
```

### 模块结束标准

- [ ] 登录接口能签发 tenant-a / tenant-b 的 Token
- [ ] `/gateway/health` 不需要 Token
- [ ] `/api/user` 必须带合法 Token
- [ ] 能画出「JWT 中间件在 proxy 之前 Abort」的流程

**全部打勾 → 进入 [③ Redis 限流](./03-redis-ratelimit.md)**

---

## 常见问题

### Q：JWT 存在 Cookie 还是 Header？

README 用 **Bearer Header**；`Authorization: Bearer <token>` 是 REST API 常见做法。

### Q：secret 泄露怎么办？

学习阶段写死在代码；阶段 4 改环境变量 + 密钥轮换。

### Q：为什么要 `c.Set("tenant")`？

后续限流、统计按租户维度，从 Context 取，不用重复解析 JWT。
