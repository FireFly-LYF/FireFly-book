"""通过 vxlink/nsfw_detector HTTP API 做图片 NSFW 检测。"""

from __future__ import annotations

import logging
from typing import Any

import httpx

from moderation_service.settings import NSFW_DETECTOR_URL, NSFW_THRESHOLD

log = logging.getLogger(__name__)


def _guess_filename(content_type: str | None) -> str:
    ct = (content_type or "").split(";", 1)[0].strip().lower()
    mapping = {
        "image/jpeg": "image.jpg",
        "image/jpg": "image.jpg",
        "image/png": "image.png",
        "image/gif": "image.gif",
        "image/webp": "image.webp",
    }
    return mapping.get(ct, "image.jpg")


def _extract_nsfw_score(payload: Any) -> float:
    """兼容常见返回结构，尽量取出 NSFW 概率。"""
    if payload is None:
        return 0.0
    if isinstance(payload, bool):
        return 1.0 if payload else 0.0
    if isinstance(payload, (int, float)):
        return float(payload)
    if not isinstance(payload, dict):
        return 0.0

    for key in ("nsfw", "nsfw_score", "score", "probability", "prob"):
        if key in payload and isinstance(payload[key], (int, float)):
            return float(payload[key])
        if key in payload and isinstance(payload[key], bool):
            return 1.0 if payload[key] else 0.0

    data = payload.get("data")
    if isinstance(data, dict):
        if "nsfw" in data and isinstance(data["nsfw"], (int, float)):
            return float(data["nsfw"])
        if "nsfw" in data and isinstance(data["nsfw"], bool):
            return 1.0 if data["nsfw"] else 0.0

    # vxlink/nsfw_detector: {"status":"success","result":{"nsfw":0.x,"normal":0.y}}
    result = payload.get("result")
    if isinstance(result, dict):
        if "nsfw" in result and isinstance(result["nsfw"], (int, float)):
            return float(result["nsfw"])
        if "nsfw" in result and isinstance(result["nsfw"], bool):
            return 1.0 if result["nsfw"] else 0.0

    label = str(payload.get("label") or payload.get("result") or payload.get("class") or "").lower()
    if label in ("nsfw", "porn", "sexy", "hentai", "unsafe"):
        conf = payload.get("confidence")
        return float(conf) if isinstance(conf, (int, float)) else 1.0
    if label in ("normal", "safe", "neutral", "sfw"):
        return 0.0

    status = payload.get("status")
    if isinstance(status, bool):
        # 部分实现用 status=true 表示 NSFW
        return 1.0 if status else 0.0
    if isinstance(status, str) and status.lower() in ("nsfw", "unsafe"):
        return 1.0

    return 0.0


def classify_bytes(
    data: bytes,
    content_type: str | None = None,
) -> tuple[bool, float, str | None]:
    """
    调用远端检测服务。
    返回 (passed, score, reason)。
    """
    if not data:
        return False, 1.0, "image:empty_file"

    url = NSFW_DETECTOR_URL.rstrip("/") + "/check"
    filename = _guess_filename(content_type)
    files = {
        "file": (filename, data, content_type or "application/octet-stream"),
    }

    try:
        with httpx.Client(timeout=httpx.Timeout(30.0, connect=5.0)) as client:
            response = client.post(url, files=files)
    except httpx.HTTPError as exc:
        log.warning("NSFW API 请求失败: %s", exc)
        raise RuntimeError(f"nsfw detector unreachable: {exc}") from exc

    if response.status_code >= 500:
        raise RuntimeError(f"nsfw detector HTTP {response.status_code}")
    if response.status_code >= 400:
        return False, 1.0, f"image:detector_http_{response.status_code}"

    try:
        payload = response.json()
    except ValueError:
        return False, 1.0, "image:detector_bad_json"

    score = _extract_nsfw_score(payload)
    log.debug("NSFW API score=%.4f payload=%s", score, payload)
    if score >= NSFW_THRESHOLD:
        return False, score, f"image:nsfw:{score:.3f}"
    return True, score, None
