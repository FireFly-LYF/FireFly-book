"""RabbitMQ 交换机、队列与 routing key（与 Java MqConstants 对齐）。"""

EXCHANGE_CONTENT = "firefly.content"
EXCHANGE_DLX = "firefly.dlx"

RK_NOTE_CREATED = "note.created"
RK_NOTE_UPDATED = "note.updated"
RK_NOTE_DELETED = "note.deleted"
RK_NOTE_MODERATED = "note.moderated"

QUEUE_MODERATION_NOTE = "moderation.note.events"
QUEUE_MODERATION_NOTE_DLQ = "moderation.note.events.dlq"
