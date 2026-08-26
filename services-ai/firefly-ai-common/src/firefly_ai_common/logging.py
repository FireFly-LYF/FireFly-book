"""统一日志配置。"""

import logging
import sys
from typing import Any


def setup_logging(level: str = "INFO", **defaults: Any) -> None:
    """配置根 logger；各服务入口调用一次即可。"""
    root = logging.getLogger()
    if root.handlers:
        root.setLevel(level.upper())
        return

    logging.basicConfig(
        level=level.upper(),
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
        stream=sys.stdout,
        **defaults,
    )
