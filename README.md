# FireFly-book

仿小红书风格的内容社区联调项目：注册登录、发笔记、赞评收藏、关注流、通知与搜索。  
**本文档仅面向 Windows（PowerShell / Docker Desktop）。**

> 教学与本地联调为主，不建议直接公网裸暴露。风险与修复进度见 [`issue.md`](./issue.md)。

---

## 项目简介

| 层级 | 内容 |
|------|------|
| 前端 | `web-console`（Vue 3 + Vite），小红书风瀑布流 UI |
| 网关 | `Gateway`（Go）：JWT、限流、按路径转发到 Java 服务 |
| 业务 | 7 个 Spring Boot 服务（用户 / 内容 / 媒体 / 互动 / 通知 / 关注流 / 搜索） |
| 基础设施 | MySQL、Redis、RabbitMQ、Elasticsearch |

请求路径：

```text
浏览器 :5173  →  Vite 代理 /api、/files
              →  Gateway :8080
              →  Java 服务 127.0.0.1:9001–9007
```

| 服务 | 端口 | 职责 |
|------|------|------|
| user-service | 9001 | 注册登录、资料、关注 |
| content-service | 9002 | 笔记 CRUD |
| media-service | 9003 | 图片上传与签名访问 |
| social-service | 9004 | 点赞、收藏、评论 |
| notify-service | 9005 | 通知 |
| feed-service | 9006 | 关注流 |
| search-service | 9007 | 笔记/用户搜索（ES） |
| Gateway | 8080 | 统一入口 |
| web-console | 5173 | 前端页面 |

**已可用：** 注册登录、发帖带图、赞藏评、通知、关注流、搜索。  
**占位中：** 全局发现流推荐、附近（LBS）、市集、私信等。

---

## 环境要求（Windows）

- Windows 10/11
- [Docker Desktop](https://www.docker.com/products/docker-desktop/)（开启并可用 `docker` 命令）
- JDK 17+、Maven Wrapper（仓库内已有 `mvnw.cmd`）
- Go 1.21+（跑 Gateway）
- Node.js 18+（跑前端，需 `npm`）
- PowerShell 5.1+（推荐用「以管理员身份」以外的普通终端即可）

---

## 使用方法（体验产品）

适合：只想把页面跑起来试用。

### 1. 准备密钥

在仓库根目录打开 PowerShell：

```powershell
cd <你的仓库路径>\FireFly-book
copy .env.example .env
notepad .env
```

填写全部必填项（不能留空）：

- `JWT_SECRET` / `ADMIN_JWT_SECRET` / `ADMIN_PASSWORD`
- `INTERNAL_HMAC_SECRET` / `MEDIA_SIGN_SECRET`
- `MYSQL_PASSWORD` / `RABBITMQ_PASSWORD`

**不要把 `.env` 提交到 Git。**

### 2. 一键启动（本机多窗口）

先确保本机已有名为 `mysql`、`redis`、`rabbitmq`、`es` 的 Docker 容器（或改用下方「全栈 Compose」）。

```powershell
.\start-all.ps1
```

也可双击 `start-all.bat`。

启动成功后浏览器打开：

- 前端：**http://localhost:5173/**
- 网关健康检查：http://localhost:8080/gateway/health

在页面注册账号即可发帖、关注、搜索。

### 3. 停止

```powershell
.\stop-all.ps1
# 若要一并停掉本机 infra 容器：
.\stop-all.ps1 -StopDocker
```

### 4. 全栈 Docker Compose（可选）

基础设施 + Java + Gateway 都进容器（前端仍建议本机 `npm run dev`）：

```powershell
copy .env.example .env
# 编辑 .env 填密钥后：
docker compose -f deploy/docker-compose.yml --env-file .env up -d --build
```

仅 Gateway 映射到宿主机 **:8080**。然后另开终端：

```powershell
cd web-console
npm install
npm run dev
```

打开 http://localhost:5173/ 。

造一批真实感测试笔记（需 Gateway 已通）：

```powershell
.\deploy\scripts\seed-notes.ps1
# 更快（不上传封面）：
.\deploy\scripts\seed-notes.ps1 -NoCovers
```

种子账号示例：`seed_momo` 等，密码默认 `123456`。

---

## 开发者使用方法（Windows）

适合：改 Java / Go / 前端代码并联调。

### 目录速览

```text
FireFly-book/
  start-all.ps1 / stop-all.ps1   本机一键启停
  .env.example                   密钥模板
  services/                      Java 微服务
  services-ai/                   Python AI（审核 / 标签 / 推荐，骨架）
  Gateway/gateway/               Go 网关
  web-console/                   Vue 前端
  deploy/                        Docker Compose（Java + AI）、造数脚本
  Java-Tutorial/md/              分章教程
  issue.md                       风险与修复清单
```

### 常用启动参数

```powershell
.\start-all.ps1                 # 全量：infra + Java + Gateway + 前端
.\start-all.ps1 -InfraOnly      # 只起 Docker 基础设施
.\start-all.ps1 -SkipFrontend   # 不起前端
.\start-all.ps1 -SkipJava       # 不起 Java（例如已在 IDE 里跑）
.\start-all.ps1 -SkipGateway
```

### 单独开发某一层

**前端**

```powershell
cd web-console
npm install
npm run dev
```

Vite 把 `/api`、`/files` 代理到 `http://127.0.0.1:8080`（见 `web-console/vite.config.js`）。

**网关**

```powershell
cd Gateway\gateway
go run ./cmd/gateway
```

配置读取环境变量中的密钥（与 `.env` / `start-all.ps1` 注入一致）。

**单个 Java 服务**

```powershell
# 首次或改了共享库时先安装：
.\services\user-service\mvnw.cmd -f .\services\firefly-internal-auth\pom.xml install -DskipTests

cd services\user-service
.\mvnw.cmd spring-boot:run
```

本机开发时业务口默认绑 `127.0.0.1`，请经 Gateway 访问，不要绕过网关伪造 `X-User-Id`。

### 自检

```powershell
curl.exe http://localhost:8080/gateway/health
curl.exe http://localhost:5173/
```

RabbitMQ 管理台：http://localhost:15672 （用户 `guest`，密码为 `.env` 里的 `RABBITMQ_PASSWORD`）。

### 文档与联调提示

- 分服务教程：[`Java-Tutorial/md/README.md`](./Java-Tutorial/md/README.md)
- 前端能力说明：[`web-console/README.md`](./web-console/README.md)
- 上线风险清单：[`issue.md`](./issue.md)
- 关注流已接 `GET /api/feed/following`；发现页目前仍是前端临时聚合，全局推荐流尚未实现

### Git 提示

```powershell
git status
git add <文件>
git commit -m "你的说明"
git push origin main
```

切勿提交 `.env`、密钥或本机绝对路径隐私信息。

---

## 许可证与说明

个人/教学联调仓库。二次使用请自行评估安全项（JWT、HMAC、媒体签名、CORS 等），生产环境请使用密钥托管并限制暴露面。
