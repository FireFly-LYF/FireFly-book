# 13 · 对接现有 Gateway

## 本章目标

让客户端只访问 **Gateway :8080**，由网关转发到各个 Java 服务。  
你的 Gateway 已具备：反向代理、JWT、限流、负载均衡、服务注册。

路径：`FireFly-book/Gateway/`

---

## 步骤 1：先保证下游自己能通

不经过网关时，下列地址应正常：

| 服务 | 探活 |
|------|------|
| user | http://127.0.0.1:9001/health |
| content | http://127.0.0.1:9002/health |
| media | http://127.0.0.1:9003/health |
| … | … |

全部 OK 再接到网关。

---

## 步骤 2：理解当前网关能力边界

根据现有文档与配置，网关可以：

- 把请求反向代理到 upstream
- 校验 Bearer JWT（租户等）
- Redis 限流、熔断
- 动态 `POST /gateway/services` 注册节点
- 健康检查（探测 `/health`）

你需要确认/扩展的两点（开发时注意）：

1. **按路径转发到不同服务**（已实现）  
   在 `Gateway/gateway/internal/config/gateway.yaml` 的 `routes` 中配置：  
   `/api/user` → :9001，`/api/note` → :9002，`/api/media`+/`files` → :9003，  
   `/api/social` → :9004，`/api/notify` → :9005（feed/search 预留 9006/9007）。  
   `proxy.strip_prefix: ""` 表示**保留完整路径**，与 Java `@RequestMapping("/api/...")` 对齐。

2. **把登录用户传给 Java**  
   网关验完 JWT 后，应把 `userId` 写入 Header，例如 `X-User-Id`，Java 只读 Header。  
   若网关暂时只校验租户、没有 userId：联调阶段客户端可继续手动带 `X-User-Id`（仅开发环境）。

---

## 步骤 3：注册下游（动态注册）

Gateway 文档中的方式（示例）：

```text
POST /gateway/services
{"url":"http://127.0.0.1:9001"}
```

对每个 Java 服务注册一次。确保服务实现了 **`GET /health` 返回 200**。

也可在 `gateway.yaml` 的 `loadbalancer.upstreams` 里写死（适合本地）：

```yaml
loadbalancer:
  strategy: round_robin
  upstreams:
    - http: http://localhost:9001
      weight: 1
```

多服务多路径时，以你网关最终支持的「路由表」配置为准；本教程要求你：**每个 Java 服务可被单独访问，且可被注册进网关**。

---

## 步骤 4：推荐的路径约定（与 Java Controller 对齐）

| 客户端请求（经网关） | Java 服务 |
|----------------------|-----------|
| `/api/user/**` | :9001 |
| `/api/note/**` | :9002 |
| `/api/media/**` | :9003 |
| `/api/social/**` | :9004 |
| `/api/notify/**` | :9005 |
| `/api/feed/**` | :9006 |
| `/api/search/**` | :9007 |

Controller 上的 `@RequestMapping` 已带 `/api/xxx` 时，网关转发时注意是否 **strip 前缀**：

- 转发保留完整路径：Java 映射写 `/api/user/...`（本教程默认）
- 若网关去掉前缀：Java 要改成 `/...` —— **两端必须一致**

---

## 步骤 5：JWT 与登录怎么配合（推荐演进）

### 阶段 1（现在）

- 注册登录在 user-service
- 请求手工加 `X-User-Id`
- 网关可先关掉严格鉴权或只用租户 JWT 做实验

### 阶段 2（推荐）

1. 登录成功后，由 **Gateway 或 auth 模块** 签发 JWT（payload 含 `uid`）
2. 客户端：`Authorization: Bearer <token>`
3. 网关验签，解析 `uid`，设置 `X-User-Id`
4. Java 服务 **信任内网 Header**（不要再盲信客户端乱传的 userId——生产应只接受网关注入）

你网关已有 JWT secret 配置（`gateway.yaml` 里 `jwt.secret`），业务 JWT 声明字段需与网关解析逻辑对齐（可能要小改网关；改之前先读 `docs/HTTP/02-jwt-auth.md`）。

---

## 步骤 6：媒体上传注意

上传走：

```text
客户端 → Gateway → media-service
```

注意网关与 Java 的 **multipart 大小限制**。若网关不适合传大文件：改为「media 返回上传凭证，客户端直传存储」，Gateway 只参与元数据 API。

---

## 步骤 7：联调检查清单

- [ ] 经网关访问 `/health` 或业务 GET 成功
- [ ] 限流触发时客户端收到明确错误（可故意调低阈值试）
- [ ] 停掉某个 Java 进程，健康检查是否摘除节点
- [ ] 日志里能看到 `X-Request-Id` 或等价追踪（若网关有）——没有可后续加

---

## 步骤 8：本地启动顺序建议

```text
1. MySQL / Redis
2. 各 Java 服务（user → content → media → …）
3. Gateway
4. 注册 upstream 或确认 yaml
5. Apifox 基地址改为 http://127.0.0.1:8080
```

---

## 本章验收

- [ ] 至少 **user + content** 可通过 Gateway 访问
- [ ] 清楚 `X-User-Id` 从哪来
- [ ] 知道路径前缀必须与转发规则一致

下一章：Redis 与 MQ → [14-redis-mq.md](./14-redis-mq.md)
