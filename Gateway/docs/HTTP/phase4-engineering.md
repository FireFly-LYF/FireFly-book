# 阶段 4：工程化与项目收尾（2~4 周）

> 所属路线：[网关项目学习路线](./README.md)  
> 前置要求：完成 [阶段 3](./phase3-readme-features.md)（或本仓库 `Gateway/` 已能跑通 HTTP 网关核心）  
> 起点代码：[`Gateway/`](../../Gateway/)（阶段 3 成果 / 当前项目）  
> 本阶段目标：把「能跑的网关」变成 README 描述的**完整交付物**  
> 原则：**先可演示，再可部署，最后可压测**

---

## 阶段目标

阶段 3 完成后，你已有：

```
单进程网关（Gateway/cmd/gateway）
  ├── HTTP  :8080   鉴权 → 限流 → LB → ReverseProxy
  ├── gRPC  :50051  透明代理 + metadata JWT
  ├── TCP   :8081   连接级 io.Copy 转发
  ├── /gateway/*    管理 API（登录、服务注册、统计）
  └── gateway.yaml  配置文件
```

阶段 4 要补齐 README 里「工程化」部分：

| 缺口 | 阶段 4 任务 |
|------|-------------|
| 只能 curl 管理 | Vue Admin 可视化 |
| 租户写死在 yaml | MySQL 持久化（可选） |
| 本地 `go run` | Docker / docker-compose 一键部署 |
| 没有性能数据 | wrk 压测 + 报告 |
| 管理 API 无鉴权 | Admin Token / RBAC |
| 缺少文档与演示 | README 截图、答辩 PPT 素材 |

**结束时应达到**：答辩 / 验收时可以用 **Docker 启动全套环境**，浏览器打开 Admin 管理服务与查看大盘，并出示 **压测对比数据**（直连 vs 经网关）。

---

## 和 README 的对应关系

| README 描述 | 阶段 4 模块 | 优先级 |
|-------------|-------------|--------|
| Vue 管理界面 | ① Admin 前端 | 必做 |
| 大盘统计与可视化 | ② 统计大盘 | 必做 |
| iPanel / Docker 部署 | ③ 容器化 | 必做 |
| wrk 压测 QPS / 延时 | ④ 压测与调优 | 必做 |
| MySQL 租户管理 | ⑤ 持久化 | 可选 |
| 配置文件 / 密钥管理 | ⑥ 配置与运维 | 建议 |
| HTTPS / TLS | ⑦ 安全加固 | 可选 |

---

## 进度对照

| 模块 | 任务 | 预计 | 状态 |
|------|------|------|------|
| **① Admin 前端** | Vue3 + Element Plus，对接现有 `/gateway/*` API | 1~2 周 | ⬜ |
| **② 统计大盘** | 图表展示租户 QPS、日请求量、下游健康 | 3~5 天 | ⬜ |
| **③ Docker 部署** | Dockerfile + compose（网关 + Redis + 下游） | 1~2 天 | ⬜ |
| **④ wrk 压测** | 直连 vs 代理 QPS、P99 延迟、报告 | 3~5 天 | ⬜ |
| **⑤ MySQL 持久化** | 租户 / 服务注册落库 | 1 周 | ⬜ |
| **⑥ 配置与运维** | 环境变量、健康检查、日志规范 | 2~3 天 | ⬜ |
| **⑦ TLS（可选）** | HTTPS 入口、gRPC TLS | 1 周 | ⬜ |

---

## 模块 ①：Vue Admin 管理界面

### 目标

用浏览器完成阶段 3 里用 curl 做的所有运维操作。

### 页面对照（最少 4 个）

| 页面 | 对接 API | 功能 |
|------|----------|------|
| 登录 | `POST /gateway/login` | 租户登录，保存 JWT |
| 服务管理 | `GET/POST/DELETE /gateway/services` | 列表、注册、下线 HTTP/gRPC/TCP 下游 |
| 限流 / 租户 | 读 yaml 或后续 MySQL API | 展示 QPS/QPD、租户白名单 |
| 网关状态 | `GET /gateway/health` | 网关存活、Redis 状态 |

### 推荐技术栈

```
Vue 3 + Vite + TypeScript
Element Plus（或 Ant Design Vue）
axios（请求拦截器自动带 Bearer Token）
vue-router（登录页 / 管理页）
```

### 目录建议

```
Gateway/
├── admin/                    # 前端独立工程
│   ├── src/
│   │   ├── api/gateway.ts    # 封装 /gateway/* 请求
│   │   ├── views/
│   │   │   ├── Login.vue
│   │   │   ├── Services.vue
│   │   │   └── Dashboard.vue
│   │   └── router/index.ts
│   └── vite.config.ts        # dev 时代理到 :8080
└── ...
```

### Vite 开发代理示例

```typescript
// admin/vite.config.ts
export default defineConfig({
  server: {
    proxy: {
      '/gateway': 'http://localhost:8080',
      '/api': 'http://localhost:8080',
    },
  },
})
```

### 服务注册表单字段（与后端一致）

```json
{
  "http": "http://localhost:9004",
  "grpc": "localhost:50054",
  "tcp": "localhost:9010",
  "weight": 1
}
```

### 检验标准

- [ ] 登录后 JWT 存入 localStorage，后续请求带 `Authorization: Bearer ...`
- [ ] 服务列表展示 `id / http / grpc / tcp / healthy / weight`
- [ ] 注册新下游后，网关日志里 upstream 列表立即更新
- [ ] 下线 unhealthy 节点后，`/api/*` 不再转发到该节点

---

## 模块 ②：统计大盘

### 目标

把 Redis 里已有的计数变成**可展示的图表**。

### 现有后端 API（可直接用）

| API | 说明 |
|-----|------|
| `GET /gateway/statistic?tenant=tenant-a&date=20260712` | 指定租户某日请求量 |
| `GET /gateway/statistics/report?tenant=tenant-a` | 最近 7 天日请求量数组 |

### 前端展示建议

| 图表 | 数据来源 |
|------|----------|
| 折线图 | `statistics/report` 的 7 天数据 |
| 数字卡片 | 今日总量、活跃租户数 |
| 表格 | 各租户今日 QPS / 日配额使用率 |

### 可选后端增强

- `GET /gateway/statistics/overview`：汇总所有租户（Admin 专用）
- 按路径 / 状态码分维度（需阶段 3 统计中间件扩展）

### 检验标准

- [ ] Dashboard 能选择租户并刷新 7 日曲线
- [ ] Redis 不可用时页面有明确错误提示（503）
- [ ] 截图可用于 README / 答辩 PPT

---

## 模块 ③：Docker 部署

### 目标

一条命令启动：**网关 + Redis + 至少 1 个 HTTP 下游**。

### 推荐 compose 服务

| 服务 | 镜像 / 构建 | 端口 |
|------|-------------|------|
| gateway | `Dockerfile` 多阶段构建 | 8080, 50051, 8081 |
| redis | `redis:7-alpine` | 6379 |
| downstream-http | 构建 `cmd/downstream_http` | 9001 |
| downstream-grpc | 构建 `cmd/downstream_grpc` | 50052 |
| downstream-tcp | 构建 `cmd/downstream_tcp` | 9010 |
| admin | `nginx` 托管 Vue 静态资源 | 80 |

### Dockerfile 要点（网关）

```dockerfile
# 构建阶段
FROM golang:1.22-alpine AS builder
WORKDIR /src
COPY Gateway/go.mod Gateway/go.sum ./
RUN go mod download
COPY Gateway/ .
RUN CGO_ENABLED=0 go build -o /gateway ./cmd/gateway

# 运行阶段
FROM alpine:3.19
COPY --from=builder /gateway /usr/local/bin/gateway
COPY Gateway/internal/config/gateway.yaml /etc/gateway/gateway.yaml
EXPOSE 8080 50051 8081
ENTRYPOINT ["gateway", "-config", "/etc/gateway/gateway.yaml"]
```

### docker-compose 片段

```yaml
services:
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]

  gateway:
    build: .
    ports:
      - "8080:8080"
      - "50051:50051"
      - "8081:8081"
    depends_on: [redis]
    volumes:
      - ./Gateway/internal/config/gateway.yaml:/etc/gateway/gateway.yaml

  downstream-http:
    build:
      context: .
      dockerfile: Dockerfile.downstream-http
    ports: ["9001:9001"]
```

### 检验标准

- [ ] `docker compose up -d` 后 curl `http://localhost:8080/gateway/health` 返回 200
- [ ] 容器内网关能连 `redis:6379`（yaml 里 addr 改为服务名）
- [ ] README 补充「Docker 启动」章节与截图

---

## 模块 ④：wrk 压测与性能调优

### 目标

复现 README 性能描述：**直连 QPS ~9k，经网关 ~6.5k，平均代理延时 ~53ms**（硬件不同会有偏差，重点是**有对比方法**）。

### 压测环境

| 组件 | 说明 |
|------|------|
| wrk | HTTP 压测工具 |
| 下游 | `downstream_http` 简单 echo / health |
| 网关 | 单下游 → 多下游逐步加压 |
| Redis | 限流开启 / 关闭各测一轮 |

### 命令示例

```powershell
# 1. 直连下游（基线）
wrk -t4 -c100 -d30s http://localhost:9001/health

# 2. 经网关（需先 login 拿 token，或临时关闭 JWT 中间件做纯代理压测）
wrk -t4 -c100 -d30s -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8080/api/health
```

### 建议记录指标

| 指标 | 含义 |
|------|------|
| Requests/sec | QPS |
| Latency Avg / P99 | 平均 / 99 分位延迟 |
| Transfer/sec | 吞吐 |
| Non-2xx | 限流 429、熔断 503 占比 |

### 调优方向（按收益排序）

1. **关闭不必要的中间件**做基线（纯代理 QPS 上限）
2. **ReverseProxy 连接池** / 下游 Keep-Alive
3. **Redis 连接池**、限流算法本地降级路径
4. **GOMAXPROCS** 与容器 CPU limit 对齐
5. 多下游 + LB 时的 consistent_hash 热点

### 检验标准

- [ ] 有表格：直连 vs 网关（JWT 开/关、Redis 开/关）
- [ ] 能解释 QPS 下降的主要原因（多一跳、序列化、锁、Redis RTT）
- [ ] 压测脚本或命令写入仓库根目录 [`bench/`](../../../../bench/README.md)

---

## 模块 ⑤：MySQL 持久化（可选）

### 目标

租户、服务注册重启后不丢失。

### 表设计草案

```sql
-- tenants
CREATE TABLE tenants (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  status TINYINT DEFAULT 1,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- upstreams
CREATE TABLE upstreams (
  id VARCHAR(256) PRIMARY KEY,
  http_url VARCHAR(512),
  grpc_addr VARCHAR(128),
  tcp_addr VARCHAR(128),
  weight INT DEFAULT 1,
  healthy TINYINT DEFAULT 1,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 改造点

| 包 | 变更 |
|----|------|
| `internal/registry` | 新增 `MySQL` 实现 `Registry` 接口 |
| `internal/tenant` | 从 DB 加载白名单，Admin CRUD |
| `cmd/gateway/main.go` | 配置 `registry.type: memory \| mysql` |

### 检验标准

- [ ] 重启网关后服务列表从 DB 恢复
- [ ] Admin 增删租户写入 MySQL

---

## 模块 ⑥：配置与运维

### 目标

生产友好：密钥不进 Git、健康检查可被 K8s / Docker 使用。

### 任务清单

| 任务 | 说明 |
|------|------|
| 环境变量覆盖 | `JWT_SECRET`、`REDIS_ADDR` 覆盖 yaml |
| 管理 API 鉴权 | `/gateway/services` 等需 Admin JWT |
| 结构化日志 | 请求 ID、tenant、target、latency |
| 优雅退出 | SIGTERM 时 `GracefulStop` gRPC、关闭 TCP listener |
| 文档 | 更新 `docs/cmd.md` 中 curl 示例（`http` 字段） |

### 检验标准

- [ ] `.env.example` 列出所有可配置项
- [ ] 未授权访问 `POST /gateway/services` 返回 401

---

## 模块 ⑦：TLS（可选）

| 场景 | 做法 |
|------|------|
| HTTP HTTPS | Gin 前面加 `autocert` 或 Nginx 终止 TLS |
| gRPC TLS | `credentials.NewTLS` + 证书挂载 |
| HTTP + gRPC 同端口 | TLS + ALPN（见 [07-tcp-grpc.md](./07-tcp-grpc.md) 讨论） |

---

## 推荐最终目录（阶段 4 完成后）

```
Gateway/
├── admin/                 # Vue Admin
├── cmd/
│   ├── gateway/
│   ├── downstream_http/
│   ├── downstream_grpc/
│   └── downstream_tcp/
├── internal/              # 已有
├── scripts/
│   ├── test-login.ps1
│   └── bench.ps1          # 新增：wrk 压测
├── Dockerfile
├── docker-compose.yml
└── .env.example

docs/
└── HTTP/
    ├── README.md
    └── phase4-engineering.md   # 本文
```

---

## 阶段 4 毕业自测

### 必做（答辩最低线）

- [ ] Docker Compose 一键启动网关 + Redis + 下游
- [ ] Admin 登录、服务注册/下线、统计图表可用
- [ ] wrk 压测报告（直连 vs 网关，有 QPS 和延迟）
- [ ] README 更新：架构图、启动方式、截图

### 加分

- [ ] MySQL 持久化租户与服务
- [ ] gRPC / TCP 在 Admin 或文档中有演示
- [ ] HTTPS 或 Nginx 反代
- [ ] GitHub Actions CI（`go test` + `go build`）

---

## 常见问题

### Q：Admin 必须 Vue 吗？

不必须。React / 纯 HTML + fetch 均可；Vue 与 README 一致，生态资料多。

### Q：压测数字和 README 对不上怎么办？

硬件、Go 版本、是否开 JWT/Redis 都会影响。**重点是方法论和对比**，在报告里写清环境即可。

### Q：阶段 3 没做 TCP/gRPC 能进阶段 4 吗？

可以。阶段 4 以 HTTP Admin + Docker + 压测为主；TCP/gRPC 可作为答辩加分演示。

### Q：iPanel 是什么？

README 提到的部署环境；本地用 **docker-compose** 等效替代，文档说明「可部署至 iPanel / 任意 Docker 主机」即可。

---

## 推荐资源

| 资源 | 链接 | 用途 |
|------|------|------|
| Vue 3 文档 | https://cn.vuejs.org/ | Admin |
| Element Plus | https://element-plus.org/zh-CN/ | UI 组件 |
| wrk | https://github.com/wrk/wrk | 压测 |
| Docker Compose | https://docs.docker.com/compose/ | 编排 |
| Gin 部署 | https://gin-gonic.com/docs/deployment/ | 生产建议 |

---

## 下一步

- 回到总路线 → [README.md](./README.md#阶段-4工程化可选项目收尾)
- 阶段 3 回顾 → [phase3-readme-features.md](./phase3-readme-features.md)
- TCP/gRPC 补做 → [07-tcp-grpc.md](./07-tcp-grpc.md) · [gRPC 目录](../gRPC/README.md)

**阶段 4 全部打勾 = README 项目完整交付，可答辩 / 写进简历。**
