# services-ai

FireFly-book 的 Python AI 栈：内容审核、AI 搜索助手。与 `services/`（Java）平级。

| 服务 | 端口 | 职责 |
|------|------|------|
| moderation-service | 9101 | MQ 消费 `note.created`，文本/图片审核 |
| assistant-service | 9102 | 用户搜索时 AI 问答（笔记 + 网络 + LLM） |

## 结构

```text
services-ai/
  firefly-ai-common/      公共库
  moderation-service/     审核
  assistant-service/      AI 搜索助手
```

## 环境

- Python 3.11+
- 可选：[uv](https://github.com/astral-sh/uv)

## 本地准备

```powershell
cd services-ai
copy .env.example .env
# 填写 RABBITMQ_PASSWORD、INTERNAL_HMAC_SECRET（与根目录 .env 一致）
# assistant 可选：LLM_API_KEY、LLM_BASE_URL

uv sync
# 或
pip install -e ./firefly-ai-common -e ./moderation-service -e ./assistant-service
```

## 启动

```powershell
# moderation-service :9101
python -m moderation_service.main

# assistant-service :9102
python -m assistant_service.main
```

或仓库根目录：

```powershell
.\deploy\scripts\start-ai.ps1
```

## 与 Java / Gateway 接线

| 路径 / 事件 | 说明 | 状态 |
|-------------|------|------|
| `note.created` → moderation（入库审核中） | content Outbox | 已实现；通过后 `note.published` → feed/search |
| `POST /api/ai/search` | Gateway → assistant-service | 已实现 |
| 站内检索 | assistant → search-service | 已实现 |

Python 业务服务不经 Gateway 直调 Java 时使用 HMAC；`assistant-service` 对外经 Gateway JWT。

## 启动方式（推荐本机 Python）

**Docker 拉 `python:3.11-slim` 卡住时（国内常见），不要用 `docker compose` 编 AI**，改用本机 Python：

```powershell
# 仓库根目录（自动 pip 安装 + 启动 9101/9102）
.\deploy\scripts\start-ai.ps1

# 只要搜索 AI、不要审核
.\deploy\scripts\start-ai.ps1 -AssistantOnly
```

Java 在 Docker、AI 在本机的组合：

```powershell
# 1. 只启动 Java + 基础设施（不 build AI，不拉 python 镜像）
docker compose -f deploy/docker-compose.yml --env-file .env up -d

# 2. 本机 AI
.\deploy\scripts\start-ai.ps1 -AssistantOnly

# 3. Gateway 容器访问本机 assistant（gateway-compose 已配 host.docker.internal:9102）
docker compose -f deploy/docker-compose.yml --env-file .env restart gateway
```

前端：`cd web-console && npm run dev`

## Docker 跑 AI（可选，需能拉 python 镜像）

仅当本机已成功 `docker pull python:3.11-slim` 时使用 profile **`ai-docker`**：

```powershell
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.ai.yml --env-file .env --profile ai-docker up -d --build assistant-service
```

并将 `gateway-compose.yaml` 里 `/api/ai` 上游改回 `http://assistant-service:9102`。
