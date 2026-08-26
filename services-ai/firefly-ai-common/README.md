# firefly-ai-common

FireFly Python AI 公共库，对标 Java `services/firefly-internal-auth`。

## 模块

| 包路径 | 职责 |
|--------|------|
| `config` | `FireflyAISettings` / `load_settings()` |
| `logging` | `setup_logging()` |
| `health` | `health_payload()` |
| `auth.hmac` | 与 `GatewayHmacSupport` 一致的 HMAC 签名 |
| `clients.http_base` | 内网 HTTP 客户端（自动带 HMAC 头） |
| `clients.content_client` | 调 content-service 内部 API |
| `clients.media_client` | 内网直读 media-service 原图（图片审核） |
| `clients.redis_client` | 热门榜 Redis ZSET |
| `events.*` | MQ 事件 Pydantic 模型（camelCase 别名） |
| `models.rank` | 推荐排序 DTO |
| `mq.*` | RabbitMQ 连接、消费者基类、发布器 |

业务逻辑（审核规则、标签提取、排序算法）放在各 `*-service`，不放在本包。

## 快速示例

```python
from firefly_ai_common.config import load_settings
from firefly_ai_common.auth import sign, verify
from firefly_ai_common.clients import ContentClient, RedisClient
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

## content-service / media-service 内部 API

| 方法 | 路径 | 说明 |
|------|------|------|
| `ContentClient.update_moderation_status` | `PATCH /api/internal/note/{id}/status` | 审核回写状态 |
| `ContentClient.set_tags` | `PUT /api/internal/note/{id}/tags` | 写入标签（待 Java 实现） |
| `MediaClient.fetch_bytes(path)` | `GET /api/internal/files?path=/files/...` | 内网读原图字节 |
