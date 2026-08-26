"""网关 / 服务间 HMAC 鉴权，对齐 Java GatewayHmacSupport。"""

from __future__ import annotations

import hashlib
import hmac
import time
from typing import Mapping, MutableMapping

HEADER_USER_ID = "X-User-Id"
HEADER_GATEWAY_TS = "X-Gateway-Ts"
HEADER_GATEWAY_SIGN = "X-Gateway-Sign"
HEADER_REQUEST_ID = "X-Request-Id"


def sign(secret: str, user_id: str | None, unix_sec: int) -> str:
    uid = "" if user_id is None else user_id
    payload = f"{uid}|{unix_sec}"
    digest = hmac.new(
        secret.encode("utf-8"),
        payload.encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()
    return digest


def verify(secret: str, user_id: str | None, sign_hex: str, unix_sec: int) -> bool:
    if not secret or not sign_hex:
        return False
    expected = sign(secret, user_id, unix_sec)
    return hmac.compare_digest(expected, sign_hex.strip())


def apply_headers(
    headers: MutableMapping[str, str],
    secret: str,
    user_id: str | None = None,
    *,
    unix_sec: int | None = None,
) -> Mapping[str, str]:
    """补齐与 GatewayHmacClientInterceptor 一致的签名头。"""
    uid = "" if user_id is None else user_id
    ts = int(time.time()) if unix_sec is None else unix_sec
    if uid:
        headers[HEADER_USER_ID] = uid
    else:
        headers.pop(HEADER_USER_ID, None)
    headers[HEADER_GATEWAY_TS] = str(ts)
    headers[HEADER_GATEWAY_SIGN] = sign(secret, uid, ts)
    return headers


def signed_headers(
    secret: str,
    user_id: str | None = None,
    *,
    request_id: str | None = None,
    unix_sec: int | None = None,
) -> dict[str, str]:
    headers: dict[str, str] = {}
    apply_headers(headers, secret, user_id, unix_sec=unix_sec)
    if request_id:
        headers[HEADER_REQUEST_ID] = request_id
    return headers
