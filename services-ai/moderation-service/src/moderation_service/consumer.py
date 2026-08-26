"""MQ 消费者：订阅 note.created。"""

from __future__ import annotations

import logging

from aio_pika.abc import AbstractRobustConnection

from firefly_ai_common.clients.content_client import ContentClient
from firefly_ai_common.clients.media_client import MediaClient
from firefly_ai_common.config import FireflyAISettings, load_settings
from firefly_ai_common.events.note_created import NoteCreatedEvent
from firefly_ai_common.mq import constants as mq
from firefly_ai_common.mq.connection import connect_rabbit, declare_content_topology, open_channel
from firefly_ai_common.mq.consumer_base import DropMessageError, QueueConsumer
from firefly_ai_common.mq.publisher import EventPublisher

from moderation_service.pipeline import process_note_created

log = logging.getLogger(__name__)


class ModerationWorker:
    """持有 MQ 连接、发布器与 content/media 客户端，处理 note.created。"""

    def __init__(self, settings: FireflyAISettings | None = None) -> None:
        self._settings = settings or load_settings()
        self._connection: AbstractRobustConnection | None = None
        self._publisher: EventPublisher | None = None
        self._content = ContentClient.from_settings(self._settings)
        self._media = MediaClient.from_settings(self._settings)
        self._consumer: QueueConsumer | None = None

    async def start(self) -> None:
        self._connection = await connect_rabbit(self._settings)
        channel = await open_channel(self._connection)
        await declare_content_topology(channel)
        self._publisher = EventPublisher(channel)
        self._consumer = QueueConsumer(
            queue_name=mq.QUEUE_MODERATION_NOTE,
            dlq_name=mq.QUEUE_MODERATION_NOTE_DLQ,
            routing_keys=[mq.RK_NOTE_CREATED],
            handler=self.handle,
            settings=self._settings,
            connection=self._connection,
        )
        await self._consumer.run()

    async def close(self) -> None:
        if self._consumer is not None:
            await self._consumer.close()
            self._consumer = None
        if self._connection is not None:
            await self._connection.close()
            self._connection = None
        self._content.close()
        self._media.close()

    async def handle(self, routing_key: str, payload: dict) -> None:
        if routing_key != mq.RK_NOTE_CREATED:
            log.debug("忽略 routing_key=%s", routing_key)
            return

        event = NoteCreatedEvent.model_validate(payload)
        if not event.is_valid():
            raise DropMessageError("缺少 note id")

        assert self._publisher is not None
        await process_note_created(
            event,
            publisher=self._publisher,
            content=self._content,
            media=self._media,
        )


def build_consumer(settings: FireflyAISettings | None = None) -> ModerationWorker:
    return ModerationWorker(settings)
