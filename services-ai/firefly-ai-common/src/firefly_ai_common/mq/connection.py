"""RabbitMQ 连接与拓扑声明。"""

from __future__ import annotations

import logging

import aio_pika
from aio_pika import ExchangeType
from aio_pika.abc import AbstractChannel, AbstractRobustConnection

from firefly_ai_common.config import FireflyAISettings, load_settings
from firefly_ai_common.mq import constants as mq

log = logging.getLogger(__name__)


def build_amqp_url(settings: FireflyAISettings | None = None) -> str:
    cfg = settings or load_settings()
    user = cfg.rabbitmq_user
    password = cfg.rabbitmq_password
    host = cfg.rabbitmq_host
    port = cfg.rabbitmq_port
    return f"amqp://{user}:{password}@{host}:{port}/"


async def connect_rabbit(
    settings: FireflyAISettings | None = None,
) -> AbstractRobustConnection:
    url = build_amqp_url(settings)
    log.info("连接 RabbitMQ %s:%s", (settings or load_settings()).rabbitmq_host, (settings or load_settings()).rabbitmq_port)
    return await aio_pika.connect_robust(url)


async def open_channel(connection: AbstractRobustConnection) -> AbstractChannel:
    channel = await connection.channel()
    await channel.set_qos(prefetch_count=10)
    return channel


async def declare_content_topology(channel: AbstractChannel) -> None:
    """声明 content 交换机与 DLX（幂等）。"""
    await channel.declare_exchange(mq.EXCHANGE_CONTENT, ExchangeType.DIRECT, durable=True)
    await channel.declare_exchange(mq.EXCHANGE_DLX, ExchangeType.DIRECT, durable=True)


async def declare_worker_queue(
    channel: AbstractChannel,
    *,
    queue_name: str,
    dlq_name: str,
    routing_keys: list[str],
    exchange_name: str = mq.EXCHANGE_CONTENT,
) -> aio_pika.abc.AbstractQueue:
    """声明业务队列 + DLQ，并绑定 routing key。"""
    await declare_content_topology(channel)

    dlq = await channel.declare_queue(dlq_name, durable=True)
    await dlq.bind(mq.EXCHANGE_DLX, routing_key=queue_name)

    queue = await channel.declare_queue(
        queue_name,
        durable=True,
        arguments={
            "x-dead-letter-exchange": mq.EXCHANGE_DLX,
            "x-dead-letter-routing-key": queue_name,
        },
    )
    exchange = await channel.get_exchange(exchange_name)
    for rk in routing_keys:
        await queue.bind(exchange, routing_key=rk)
    return queue
