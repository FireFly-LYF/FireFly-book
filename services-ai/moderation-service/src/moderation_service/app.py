"""FastAPI 应用（骨架：仅 /health）。"""

import os

from fastapi import FastAPI

from firefly_ai_common.health import health_payload

SERVICE_NAME = "moderation-service"
DEFAULT_PORT = 9101


def create_app() -> FastAPI:
    app = FastAPI(title=SERVICE_NAME, version="0.1.0")
    port = int(os.getenv("MODERATION_SERVICE_PORT", DEFAULT_PORT))

    @app.get("/health")
    def health() -> dict:
        return health_payload(SERVICE_NAME, port)

    return app
