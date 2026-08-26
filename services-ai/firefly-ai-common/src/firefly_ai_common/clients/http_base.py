"""HTTP 客户端基座：超时、HMAC 头、请求 ID。"""

from __future__ import annotations

import logging
from typing import Any
from uuid import uuid4

import httpx

from firefly_ai_common.auth.hmac import HEADER_REQUEST_ID, signed_headers
from firefly_ai_common.config import FireflyAISettings, load_settings

log = logging.getLogger(__name__)


class InternalHttpError(RuntimeError):
    """内网 HTTP 调用失败。"""

    def __init__(self, message: str, *, status_code: int | None = None) -> None:
        super().__init__(message)
        self.status_code = status_code


class InternalHttpClient:
    """服务间直调客户端，自动附带 Gateway 同款 HMAC。"""

    def __init__(
        self,
        base_url: str,
        *,
        secret: str | None = None,
        enabled: bool = True,
        connect_timeout_sec: float = 2.0,
        read_timeout_sec: float = 3.0,
        client: httpx.Client | None = None,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._secret = secret or ""
        self._enabled = enabled and bool(self._secret)
        timeout = httpx.Timeout(read_timeout_sec, connect=connect_timeout_sec)
        self._owns_client = client is None
        self._client = client or httpx.Client(timeout=timeout)

    @classmethod
    def from_settings(
        cls,
        settings: FireflyAISettings | None = None,
        *,
        base_url: str | None = None,
    ) -> InternalHttpClient:
        cfg = settings or load_settings()
        return cls(
            base_url or cfg.content_base_url,
            secret=cfg.internal_hmac_secret,
            enabled=cfg.internal_auth_enabled,
            connect_timeout_sec=cfg.http_connect_timeout_sec,
            read_timeout_sec=cfg.http_read_timeout_sec,
        )

    def close(self) -> None:
        if self._owns_client:
            self._client.close()

    def __enter__(self) -> InternalHttpClient:
        return self

    def __exit__(self, *args: object) -> None:
        self.close()

    def request(
        self,
        method: str,
        path: str,
        *,
        user_id: str | None = None,
        request_id: str | None = None,
        params: dict[str, Any] | None = None,
        json: Any | None = None,
        headers: dict[str, str] | None = None,
    ) -> httpx.Response:
        url = f"{self._base_url}{path}"
        req_headers = dict(headers or {})
        rid = request_id or req_headers.get(HEADER_REQUEST_ID) or uuid4().hex
        req_headers[HEADER_REQUEST_ID] = rid
        if self._enabled:
            req_headers.update(signed_headers(self._secret, user_id, request_id=rid))

        log.debug("HTTP %s %s", method.upper(), url)
        try:
            response = self._client.request(
                method,
                url,
                params=params,
                json=json,
                headers=req_headers,
            )
        except httpx.HTTPError as exc:
            raise InternalHttpError(f"HTTP 请求失败: {exc}") from exc
        return response

    def get_json(
        self,
        path: str,
        *,
        user_id: str | None = None,
        params: dict[str, Any] | None = None,
    ) -> Any:
        response = self.request("GET", path, user_id=user_id, params=params)
        return self._parse_json(response)

    def get_bytes(
        self,
        path: str,
        *,
        user_id: str | None = None,
        params: dict[str, Any] | None = None,
    ) -> tuple[bytes, str]:
        response = self.request("GET", path, user_id=user_id, params=params)
        if response.status_code >= 400:
            raise InternalHttpError(
                f"HTTP {response.status_code}: {response.text[:500]}",
                status_code=response.status_code,
            )
        content_type = response.headers.get("content-type", "application/octet-stream")
        return response.content, content_type.split(";", 1)[0].strip()

    def post_json(
        self,
        path: str,
        *,
        user_id: str | None = None,
        body: Any | None = None,
    ) -> Any:
        response = self.request("POST", path, user_id=user_id, json=body)
        return self._parse_json(response)

    def patch_json(
        self,
        path: str,
        *,
        user_id: str | None = None,
        body: Any | None = None,
    ) -> Any:
        response = self.request("PATCH", path, user_id=user_id, json=body)
        return self._parse_json(response)

    def put_json(
        self,
        path: str,
        *,
        user_id: str | None = None,
        body: Any | None = None,
    ) -> Any:
        response = self.request("PUT", path, user_id=user_id, json=body)
        return self._parse_json(response)

    @staticmethod
    def _parse_json(response: httpx.Response) -> Any:
        if response.status_code >= 400:
            raise InternalHttpError(
                f"HTTP {response.status_code}: {response.text[:500]}",
                status_code=response.status_code,
            )
        if not response.content:
            return None
        return response.json()
