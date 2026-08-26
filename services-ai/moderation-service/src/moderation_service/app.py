"""FastAPI 应用：/health + 后台 MQ 消费者。"""

from __future__ import annotations

import os
from contextlib import asynccontextmanager

from fastapi import FastAPI

from firefly_ai_common.config import load_settings
from firefly_ai_common.health import health_payload
from moderation_service.consumer import ModerationWorker, build_consumer

SERVICE_NAME = "moderation-service"
DEFAULT_PORT = 9101


@asynccontextmanager
async def _lifespan(app: FastAPI):
    settings = load_settings()
    worker: ModerationWorker = build_consumer(settings)
    app.state.moderation_worker = worker
    await worker.start()
    yield
    await worker.close()


def create_app() -> FastAPI:
    app = FastAPI(title=SERVICE_NAME, version="0.1.0", lifespan=_lifespan)
    port = int(os.getenv("MODERATION_SERVICE_PORT", DEFAULT_PORT))

    @app.get("/health")
    def health() -> dict:
        return health_payload(SERVICE_NAME, port, phase="ready")

    return app
