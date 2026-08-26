"""进程入口。"""

import os

import uvicorn

from firefly_ai_common.logging import setup_logging
from moderation_service.app import create_app, DEFAULT_PORT

app = create_app()


def main() -> None:
    setup_logging(os.getenv("LOG_LEVEL", "INFO"))
    port = int(os.getenv("MODERATION_SERVICE_PORT", DEFAULT_PORT))
    uvicorn.run(app, host="0.0.0.0", port=port, log_level="info")


if __name__ == "__main__":
    main()
