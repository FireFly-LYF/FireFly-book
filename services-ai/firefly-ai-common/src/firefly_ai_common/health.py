"""健康检查响应（骨架）。"""

from typing import Any


def health_payload(service: str, port: int) -> dict[str, Any]:
    return {
        "status": "ok",
        "service": service,
        "port": port,
        "phase": "skeleton",
    }
