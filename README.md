# FireFly-book

仿小红书风格的内容社区：注册登录、发笔记（图文）、赞评藏、关注流、通知、搜索，以及可选的内容审核与 AI 搜索助手。  
技术栈为 Go 网关 + Java 微服务 + Vue 前端，配套 MySQL / Redis / RabbitMQ / Elasticsearch；支持本机一键启动与 Docker Compose 部署。

---

## 架构

| 层级 | 目录 | 说明 |
|------|------|------|
| 前端 | `web-console` | Vue 3 + Vite |
| 网关 | `Gateway` | Go：JWT、CORS、分级限流、超时熔断、签名转发 |
| 业务 | `services/` | 7 个 Spring Boot 服务 |
| AI | `services-ai/` | 内容审核（文本 + NSFW）、AI 搜索助手 |
| 基础设施 | Docker | MySQL、Redis、RabbitMQ、ES（IK） |
| 压测归档 | `bench/` | 网关/业务/L1/NSFW 脚本与报告（与业务隔离） |

```text
浏览器 :5173 → Gateway :8080 → Java :9001–9007
                              → assistant :9102（可选）
note.created → moderation :9101 → 回写 status → note.published → feed / search
```

| 服务 | 端口 | 职责 |
|------|------|------|
| user-service | 9001 | 注册登录、资料、关注、批量摘要 |
| content-service | 9002 | 笔记 CRUD、审核状态、Outbox |
| media-service | 9003 | 上传与签名访问 |
| social-service | 9004 | 赞、藏、评论（楼中楼） |
| notify-service | 9005 | 通知 |
| feed-service | 9006 | 关注流写扩散 |
| search-service | 9007 | ES 搜索 |
| moderation-service | 9101 | 先审后发（文本 + NSFW HTTP） |
| assistant-service | 9102 | AI 搜索（引用 + Web 过滤） |
| Gateway | 8080 | 统一入口 |
| web-console | 5173 | 页面 |

---

## 能力一览

| 域 | 已落地 |
|----|--------|
| 账号 | 注册/登录、Refresh + 设备指纹、BCrypt |
| 内容 | 发帖带图、详情/列表、`Idempotency-Key` |
| 审核 | 入库审核中 → NSFW/文本审核 → 通过发 `note.published`；拒绝不可见 |
| 互动 | 赞/藏幂等、楼中楼评论、评论作者摘要 |
| 通知 | 列表/已读、写侧幂等 |
| 信息流 | `GET /api/feed/following` |
| 搜索 | ES + IK；可选 AI 问答与引用 |
| 网关 | JWT、白名单 CORS、限流、熔断、媒体签名 |
| 缓存 | Redis L2 + Caffeine L1（详情/点赞计数/通知等，可开关） |
| 可观测 | Actuator、RequestId |

**未做：** 全局推荐发现流、LBS、市集、私信、完整个人页编辑等。

压测结论与脚本见 [`bench/README.md`](./bench/README.md)。

---

## 环境要求

- Windows 10/11、Docker Desktop  
- JDK 17+、Go 1.21+、Node.js 18+  
- （AI）Python 3.11+；审图需 `vxlink/nsfw_detector`（本机或 compose）

---

## 快速启动

### 1. 密钥

```powershell
cd <仓库路径>\FireFly-book
copy .env.example .env
notepad .env
```

必填：`JWT_SECRET`、`ADMIN_JWT_SECRET`、`ADMIN_PASSWORD`、`INTERNAL_HMAC_SECRET`、`MEDIA_SIGN_SECRET`、`MYSQL_PASSWORD`、`RABBITMQ_PASSWORD`。  
**勿提交 `.env`。**

### 2. 本机一键

需已有 `mysql` / `redis` / `rabbitmq` / `es` 容器，或改用下方 Compose：

```powershell
.\start-all.ps1
# .\start-all.ps1 -InfraOnly | -SkipFrontend | -SkipJava | -SkipGateway
```

- 前端：http://localhost:5173/  
- 网关：http://localhost:8080/gateway/health  

```powershell
.\stop-all.ps1
.\stop-all.ps1 -StopDocker
```

### 3. Compose（infra + Java + Gateway）

```powershell
docker compose -f deploy/docker-compose.yml --env-file .env up -d --build
cd web-console; npm install; npm run dev
```

造数：`.\deploy\scripts\seed-notes.ps1`（可选 `-NoCovers`）。种子账号如 `seed_momo` / `123456`。

### 4. AI（审核 / 搜索助手）

`start-all.ps1` 默认不起 Python。需要时：

```powershell
docker run -d -p 3333:3333 --name nsfw-detector vxlink/nsfw_detector:v1.12
.\deploy\scripts\start-ai.ps1
# .\deploy\scripts\start-ai.ps1 -AssistantOnly
```

详见 [`services-ai/README.md`](./services-ai/README.md)。

---

## 开发联调

```text
services/          Java + firefly-internal-auth
services-ai/       moderation / assistant
Gateway/gateway/   go run ./cmd/gateway
web-console/       npm run dev（代理 /api、/files → :8080）
deploy/            compose、seed、start-ai.ps1
bench/             压测（可选）
issue.md           风险与修复状态
```

单服务示例：

```powershell
.\services\user-service\mvnw.cmd -f .\services\firefly-internal-auth\pom.xml install -DskipTests
cd services\user-service
.\mvnw.cmd spring-boot:run
```

业务口默认绑 `127.0.0.1`，须经 Gateway，勿伪造 `X-User-Id`。

自检：

```powershell
curl.exe http://localhost:8080/gateway/health
curl.exe http://localhost:5173/
```

RabbitMQ：http://localhost:15672（`guest` / `.env` 中 `RABBITMQ_PASSWORD`）。

---

## 文档

| 文档 | 说明 |
|------|------|
| [`issue.md`](./issue.md) | 上线风险与修复进度（必读） |
| [`services-ai/README.md`](./services-ai/README.md) | AI 启动与接线 |
| [`bench/README.md`](./bench/README.md) | 压测脚本与归档报告 |
| [`web-console/README.md`](./web-console/README.md) | 前端已接能力 |
| [`Java-Tutorial/md/`](./Java-Tutorial/md/README.md) | 历史分章笔记（非上线手册） |

---

## 许可证与上线注意

个人项目。本文档面向 Windows（PowerShell / Docker Desktop）本机或内网部署。  
不建议直接公网裸暴露；上线前请对照 [`issue.md`](./issue.md) 确认 P0/P1，并做好生产密钥托管与 CORS/暴露面收敛。
