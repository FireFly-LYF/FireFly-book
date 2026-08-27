"""校验网关注入的 HMAC 身份头（对齐 Java GatewayHmacFilter）。"""

from __future__ import annotations

import time
from typing import Mapping

from firefly_ai_common.auth.hmac import (
    HEADER_GATEWAY_SIGN,
    HEADER_GATEWAY_TS,
    HEADER_USER_ID,
    verify,
)


class GatewayAuthError(Exception):
    def __init__(self, message: str, status_code: int = 401) -> None:
        super().__init__(message)
        self.status_code = status_code


def verify_gateway_headers(
    headers: Mapping[str, str],
    secret: str,
    *,
    skew_sec: int = 60,
    enabled: bool = True,
) -> str:
    """校验成功返回 X-User-Id（可能为空字符串）。"""
    if not enabled:
        return headers.get(HEADER_USER_ID) or ""

    if not secret:
        raise GatewayAuthError("internal auth not configured")

    user_id = headers.get(HEADER_USER_ID) or ""
    ts_raw = headers.get(HEADER_GATEWAY_TS)
    sign_hex = headers.get(HEADER_GATEWAY_SIGN)
    if not ts_raw or not sign_hex:
        raise GatewayAuthError("missing gateway signature")

    try:
        ts = int(ts_raw.strip())
    except ValueError:
        raise GatewayAuthError("invalid gateway ts")

    now = int(time.time())
    skew = max(1, skew_sec)
    if abs(now - ts) > skew:
        raise GatewayAuthError("gateway signature expired")

    if not verify(secret, user_id, sign_hex.strip(), ts):
        raise GatewayAuthError("invalid gateway signature")

    return user_id
