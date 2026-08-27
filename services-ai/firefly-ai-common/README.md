# firefly-ai-common

FireFly Python AI 公共库，对标 Java `services/firefly-internal-auth`。

## 模块

| 包路径 | 职责 |
|--------|------|
| `config` | `FireflyAISettings` / `load_settings()` |
| `logging` | `setup_logging()` |
| `health` | `health_payload()` |
| `auth` | HMAC 签名 / 网关头校验 |
| `clients.http_base` | 内网 HTTP 客户端（自动带 HMAC 头） |
| `clients.content_client` | 调 content-service 内部 API |
| `clients.media_client` | 内网直读 media-service 原图（图片审核） |
| `clients.search_client` | 调 search-service 笔记检索 |
| `events.*` | MQ 事件 Pydantic 模型（camelCase 别名） |
| `mq.*` | RabbitMQ 连接、消费者基类、发布器 |

业务逻辑（审核规则、LLM 编排）放在各 `*-service`，不放在本包。

## 快速示例

```python
from firefly_ai_common.config import load_settings
from firefly_ai_common.auth import sign, verify, verify_gateway_headers
from firefly_ai_common.clients import ContentClient, SearchClient
from firefly_ai_common.events import NoteCreatedEvent, NoteModeratedEvent
from firefly_ai_common.mq import QueueConsumer, constants as mq

settings = load_settings()

# HMAC（服务间直调 content-service）
ts = 1700000000
sig = sign(settings.internal_hmac_secret, "", ts)

# MQ 消费者（moderation-service）
async def on_note(routing_key: str, payload: dict) -> None:
    event = NoteCreatedEvent.model_validate(payload)
    if not event.is_valid():
        return

consumer = QueueConsumer(
    queue_name=mq.QUEUE_MODERATION_NOTE,
    dlq_name=mq.QUEUE_MODERATION_NOTE_DLQ,
    routing_keys=[mq.RK_NOTE_CREATED],
    handler=on_note,
)
```

## 环境变量

见 `services-ai/.env.example`（`RABBITMQ_*`、`INTERNAL_HMAC_SECRET`、`CONTENT_BASE_URL` 等）。

## content-service / media-service / search-service 内部 API

| 方法 | 路径 | 说明 |
|------|------|------|
| `ContentClient.update_moderation_status` | `PATCH /api/internal/note/{id}/status` | 审核回写状态 |
| `MediaClient.fetch_bytes(path)` | `GET /api/internal/files?path=/files/...` | 内网读原图字节 |
| `SearchClient.search_notes(q)` | `GET /api/search/note` | 笔记全文检索 |
