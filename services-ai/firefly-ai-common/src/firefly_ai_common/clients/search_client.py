"""search-service 客户端：笔记全文检索。"""

from __future__ import annotations

import logging
from typing import Any

from firefly_ai_common.clients.http_base import InternalHttpClient, InternalHttpError
from firefly_ai_common.config import FireflyAISettings, load_settings

log = logging.getLogger(__name__)


class SearchClientError(RuntimeError):
    pass


class SearchClient:
    def __init__(self, http: InternalHttpClient | None = None) -> None:
        self._http = http or self._http_from_settings()

    @staticmethod
    def _http_from_settings(settings: FireflyAISettings | None = None) -> InternalHttpClient:
        cfg = settings or load_settings()
        return InternalHttpClient(
            cfg.search_base_url,
            secret=cfg.internal_hmac_secret,
            enabled=cfg.internal_auth_enabled,
            connect_timeout_sec=cfg.http_connect_timeout_sec,
            read_timeout_sec=max(cfg.http_read_timeout_sec, 5.0),
        )

    @classmethod
    def from_settings(cls, settings: FireflyAISettings | None = None) -> SearchClient:
        return cls(cls._http_from_settings(settings))

    def close(self) -> None:
        self._http.close()

    def search_notes(self, q: str, *, page: int = 1, size: int = 5) -> list[dict[str, Any]]:
        if not q or not q.strip():
            return []
        try:
            raw = self._http.get_json(
                "/api/search/note",
                params={"q": q.strip(), "page": page, "size": size},
            )
        except InternalHttpError as exc:
            raise SearchClientError(str(exc)) from exc
        return self._unwrap_list(raw)

    @staticmethod
    def _unwrap_list(raw: Any) -> list[dict[str, Any]]:
        if raw is None:
            return []
        if isinstance(raw, list):
            return [item for item in raw if isinstance(item, dict)]
        if isinstance(raw, dict):
            if "code" in raw:
                code = raw.get("code")
                if code != 0:
                    msg = raw.get("message") or f"code={code}"
                    raise SearchClientError(msg)
                data = raw.get("data")
                if isinstance(data, list):
                    return [item for item in data if isinstance(item, dict)]
                return []
            if "data" in raw and isinstance(raw["data"], list):
                return [item for item in raw["data"] if isinstance(item, dict)]
        return []
