"""MQ 消费者基类：ack/nack、DLQ、JSON 反序列化。"""

from __future__ import annotations

import asyncio
import inspect
import json
import logging
from collections.abc import Awaitable, Callable
from typing import Any

import aio_pika
from aio_pika.abc import AbstractIncomingMessage, AbstractRobustConnection

from firefly_ai_common.config import FireflyAISettings, load_settings
from firefly_ai_common.mq.connection import connect_rabbit, declare_worker_queue, open_channel

log = logging.getLogger(__name__)

MessageHandler = Callable[[str, dict[str, Any]], Awaitable[None] | None]


class DropMessageError(Exception):
    """不可重试：ACK 后丢弃。"""


class RetryableMessageError(Exception):
    """可重试：NACK requeue。"""


class QueueConsumer:
    """绑定单个业务队列，按 routing key 分发 JSON 载荷。"""

    def __init__(
        self,
        *,
        queue_name: str,
        dlq_name: str,
        routing_keys: list[str],
        handler: MessageHandler,
        settings: FireflyAISettings | None = None,
        connection: AbstractRobustConnection | None = None,
    ) -> None:
        self._queue_name = queue_name
        self._dlq_name = dlq_name
        self._routing_keys = routing_keys
        self._handler = handler
        self._settings = settings or load_settings()
        self._external_connection = connection
        self._connection: AbstractRobustConnection | None = None
        self._owns_connection = connection is None

    async def run(self) -> None:
        self._connection = self._external_connection or await connect_rabbit(self._settings)
        channel = await open_channel(self._connection)
        queue = await declare_worker_queue(
            channel,
            queue_name=self._queue_name,
            dlq_name=self._dlq_name,
            routing_keys=self._routing_keys,
        )
        log.info(
            "开始消费 queue=%s routing_keys=%s",
            self._queue_name,
            ",".join(self._routing_keys),
        )
        await queue.consume(self._on_message, no_ack=False)

    async def close(self) -> None:
        if self._owns_connection and self._connection is not None:
            await self._connection.close()
            self._connection = None

    async def _on_message(self, message: AbstractIncomingMessage) -> None:
        routing_key = message.routing_key or ""
        try:
            payload = json.loads(message.body.decode("utf-8"))
            if not isinstance(payload, dict):
                raise DropMessageError("消息体不是 JSON 对象")
            await self._dispatch(routing_key, payload)
        except DropMessageError as exc:
            log.warning("丢弃消息 routing_key=%s: %s", routing_key, exc)
            await message.ack()
        except RetryableMessageError as exc:
            log.warning("重试消息 routing_key=%s: %s", routing_key, exc)
            await message.nack(requeue=True)
        except Exception:
            log.exception("处理消息失败 routing_key=%s，送入 DLQ", routing_key)
            await message.nack(requeue=False)
        else:
            await message.ack()

    async def _dispatch(self, routing_key: str, payload: dict[str, Any]) -> None:
        result = self._handler(routing_key, payload)
        if inspect.isawaitable(result):
            await result


async def run_consumer(consumer: QueueConsumer) -> None:
    """阻塞运行消费者，直到连接断开。"""
    await consumer.run()
    try:
        await asyncio.Future()
    finally:
        await consumer.close()
