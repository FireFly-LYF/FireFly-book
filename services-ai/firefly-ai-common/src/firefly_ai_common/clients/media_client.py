"""media-service 内网 API：直读磁盘原图（不经签名 URL）。"""

from __future__ import annotations

import logging

from firefly_ai_common.clients.http_base import InternalHttpClient, InternalHttpError
from firefly_ai_common.config import FireflyAISettings, load_settings

log = logging.getLogger(__name__)


class MediaClientError(RuntimeError):
    pass


class MediaClient:
    """内网读取 /files/... 原图字节，供图片审核使用。"""

    def __init__(self, http: InternalHttpClient | None = None) -> None:
        self._http = http or self._default_http()

    @staticmethod
    def _default_http() -> InternalHttpClient:
        settings = load_settings()
        return InternalHttpClient(
            settings.media_base_url,
            secret=settings.internal_hmac_secret,
            enabled=settings.internal_auth_enabled,
            connect_timeout_sec=settings.http_connect_timeout_sec,
            read_timeout_sec=max(settings.http_read_timeout_sec, 15.0),
        )

    @classmethod
    def from_settings(cls, settings: FireflyAISettings | None = None) -> MediaClient:
        cfg = settings or load_settings()
        http = InternalHttpClient(
            cfg.media_base_url,
            secret=cfg.internal_hmac_secret,
            enabled=cfg.internal_auth_enabled,
            connect_timeout_sec=cfg.http_connect_timeout_sec,
            read_timeout_sec=max(cfg.http_read_timeout_sec, 15.0),
        )
        return cls(http)

    def close(self) -> None:
        self._http.close()

    def __enter__(self) -> MediaClient:
        return self

    def __exit__(self, *args: object) -> None:
        self.close()

    def fetch_bytes(self, path: str) -> tuple[bytes, str]:
        """按规范路径读取原图，返回 (bytes, content_type)。"""
        if not path or not path.strip():
            raise MediaClientError("path 不能为空")
        try:
            return self._http.get_bytes("/api/internal/files", params={"path": path.strip()})
        except InternalHttpError as exc:
            raise MediaClientError(str(exc)) from exc
