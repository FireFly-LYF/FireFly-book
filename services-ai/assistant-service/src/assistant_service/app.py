"""FastAPI 应用组装：health、路由挂载、启动预热、网关鉴权。"""

from __future__ import annotations

import asyncio
import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Request

from firefly_ai_common.auth import GatewayAuthError, verify_gateway_headers
from firefly_ai_common.config import load_settings
from firefly_ai_common.health import health_payload

from assistant_service.agent.llm import generate_answer
from assistant_service.settings import load_assistant_settings

SERVICE_NAME = "assistant-service"
DEFAULT_PORT = 9102
log = logging.getLogger(__name__)


def gateway_user_id(request: Request) -> str:
    """校验网关注入的 HMAC 头，返回 X-User-Id。"""
    cfg = load_settings()
    try:
        return verify_gateway_headers(
            request.headers,
            cfg.internal_hmac_secret,
            skew_sec=cfg.hmac_skew_sec,
            enabled=cfg.internal_auth_enabled,
        )
    except GatewayAuthError as exc:
        raise HTTPException(status_code=exc.status_code, detail=str(exc)) from exc


@asynccontextmanager
async def _lifespan(app: FastAPI):
    cfg = load_assistant_settings()
    if cfg.llm_api_key:
        try:
            await asyncio.to_thread(
                generate_answer,
                "ping",
                "（无相关站内笔记）",
                "（无网络搜索结果）",
            )
            log.info("LLM 预热完成 model=%s", cfg.llm_model)
        except Exception as exc:
            log.warning("LLM 预热失败: %s", exc)
    yield


def create_app() -> FastAPI:
    from assistant_service.routes.search import router as search_router

    app = FastAPI(title=SERVICE_NAME, version="0.1.0", lifespan=_lifespan)
    port = int(os.getenv("ASSISTANT_SERVICE_PORT", DEFAULT_PORT))

    @app.get("/health")
    def health() -> dict:
        return health_payload(SERVICE_NAME, port)

    app.include_router(search_router)
    return app
