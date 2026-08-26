# services-ai

FireFly-book 的 Python AI 栈：审核、标签、推荐。与 `services/`（Java）平级。

当前 **moderation-service** 已实现 P0 规则审核；tagging / recommend 仍为骨架。

## 结构

```text
services-ai/
  firefly-ai-common/      公共库（对标 firefly-internal-auth）
  moderation-service/     审核（MQ 消费 note.created）
  tagging-service/         标签（MQ 消费 note.moderated）
  recommend-service/       推荐排序（HTTP，供 feed-service 调用）
```

## 环境

- Python 3.11+
- 可选：[uv](https://github.com/astral-sh/uv)（workspace 联调更方便）

## 本地准备（骨架阶段）

```powershell
cd services-ai
copy .env.example .env
# 填写 RABBITMQ_PASSWORD、INTERNAL_HMAC_SECRET（与根目录 .env 一致）

# 方式 A：uv
uv sync

# 方式 B：pip
pip install -e ./firefly-ai-common
pip install -e ./moderation-service
pip install -e ./tagging-service
pip install -e ./recommend-service
```

## 启动（占位）

各服务入口仅暴露 `/health`，无业务逻辑：

```powershell
# moderation-service :9101
python -m moderation_service.main

# tagging-service :9102
python -m tagging_service.main

# recommend-service :9103
python -m recommend_service.main
```

或在仓库根目录：

```powershell
.\deploy\scripts\start-ai.ps1
```

## 与 Java 的接线（待实现）

| 事件 / API | 生产者 | 消费者 / 调用方 | 状态 |
|------------|--------|-----------------|------|
| `note.created` | content-service | moderation-service | 已实现 |
| `note.moderated` | moderation-service | tagging-service | 审核侧已实现 |
| `POST /internal/rank` | feed-service | recommend-service | 待实现 |

Python 服务不注册 Gateway，仅内网 + HMAC。

## Docker（可选，仓库根目录执行）

仅 AI 栈：

```powershell
docker compose -f deploy/docker-compose.ai.yml --env-file .env up -d --build
```

与全栈 Java 组合：

```powershell
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.ai.yml --env-file .env up -d --build
```
