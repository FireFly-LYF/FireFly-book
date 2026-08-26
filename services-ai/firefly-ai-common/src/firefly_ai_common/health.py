"""健康检查响应。"""

from typing import Any

from firefly_ai_common import __version__


def health_payload(
    service: str,
    port: int,
    *,
    phase: str = "ready",
    extra: dict[str, Any] | None = None,
) -> dict[str, Any]:
    payload: dict[str, Any] = {
        "status": "ok",
        "service": service,
        "port": port,
        "phase": phase,
        "version": __version__,
    }
    if extra:
        payload.update(extra)
    return payload
