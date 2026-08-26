"""MQ 事件发布。"""

from __future__ import annotations

import json
import logging
from typing import Any

import aio_pika
from aio_pika import DeliveryMode, Message
from aio_pika.abc import AbstractChannel, AbstractRobustConnection
from pydantic import BaseModel

from firefly_ai_common.config import FireflyAISettings, load_settings
from firefly_ai_common.mq.connection import declare_content_topology, open_channel
from firefly_ai_common.mq import constants as mq

log = logging.getLogger(__name__)


class EventPublisher:
    def __init__(self, channel: AbstractChannel) -> None:
        self._channel = channel

    @classmethod
    async def connect(cls, settings: FireflyAISettings | None = None) -> tuple[EventPublisher, AbstractRobustConnection]:
        from firefly_ai_common.mq.connection import connect_rabbit

        connection = await connect_rabbit(settings)
        channel = await open_channel(connection)
        await declare_content_topology(channel)
        return cls(channel), connection

    async def publish(
        self,
        routing_key: str,
        payload: BaseModel | dict[str, Any],
        *,
        exchange_name: str = mq.EXCHANGE_CONTENT,
    ) -> None:
        if isinstance(payload, BaseModel):
            body = payload.model_dump(by_alias=True, exclude_none=True)
        else:
            body = payload
        exchange = await self._channel.get_exchange(exchange_name)
        message = Message(
            body=json.dumps(body, ensure_ascii=False).encode("utf-8"),
            content_type="application/json",
            delivery_mode=DeliveryMode.PERSISTENT,
        )
        await exchange.publish(message, routing_key=routing_key)
        log.debug("已发布 MQ 事件 exchange=%s routing_key=%s", exchange_name, routing_key)

    async def publish_note_moderated(self, payload: BaseModel) -> None:
        await self.publish(mq.RK_NOTE_MODERATED, payload)

    async def publish_note_tagged(self, payload: BaseModel) -> None:
        await self.publish(mq.RK_NOTE_TAGGED, payload)
