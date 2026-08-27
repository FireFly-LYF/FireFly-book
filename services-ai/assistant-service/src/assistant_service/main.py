"""进程入口。"""

import os

import uvicorn

from firefly_ai_common.logging import setup_logging
from assistant_service.app import DEFAULT_PORT, create_app

app = create_app()


def main() -> None:
    setup_logging(os.getenv("LOG_LEVEL", "INFO"))
    port = int(os.getenv("ASSISTANT_SERVICE_PORT", DEFAULT_PORT))
    uvicorn.run(app, host="0.0.0.0", port=port, log_level="info")


if __name__ == "__main__":
    main()
