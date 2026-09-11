"""content-service 内部 API 客户端。"""

from __future__ import annotations

import logging
from typing import Any

from pydantic import BaseModel, ConfigDict, Field

from firefly_ai_common.clients.http_base import InternalHttpClient
from firefly_ai_common.config import FireflyAISettings, load_settings

log = logging.getLogger(__name__)


class ApiEnvelope(BaseModel):
    code: int
    message: str
    data: Any | None = None


class NoteModerationPatch(BaseModel):
    """PATCH /api/internal/note/{id}/status：1=通过发布，3=拒绝。"""

    model_config = ConfigDict(populate_by_name=True)

    status: int
    reason: str | None = None


class NoteTagsPut(BaseModel):
    """PUT /api/internal/note/{id}/tags 请求体（Java 侧待实现）。"""

    model_config = ConfigDict(populate_by_name=True)

    tags: list[str] = Field(default_factory=list)


class ContentClientError(RuntimeError):
    pass


class ContentClient:
    """调用 content-service 内网接口，不经 Gateway。"""

    def __init__(self, http: InternalHttpClient | None = None) -> None:
        self._http = http or InternalHttpClient.from_settings()

    @classmethod
    def from_settings(cls, settings: FireflyAISettings | None = None) -> ContentClient:
        return cls(InternalHttpClient.from_settings(settings))

    def close(self) -> None:
        self._http.close()

    def __enter__(self) -> ContentClient:
        return self

    def __exit__(self, *args: object) -> None:
        self.close()

    def update_moderation_status(
        self,
        note_id: int,
        *,
        status: int,
        reason: str | None = None,
    ) -> None:
        body = NoteModerationPatch(status=status, reason=reason)
        self._call(
            self._http.patch_json(
                f"/api/internal/note/{note_id}/status",
                body=body.model_dump(by_alias=True, exclude_none=True),
            )
        )

    def set_tags(self, note_id: int, tags: list[str]) -> None:
        body = NoteTagsPut(tags=tags)
        self._call(
            self._http.put_json(
                f"/api/internal/note/{note_id}/tags",
                body=body.model_dump(by_alias=True),
            )
        )

    def get_note(self, note_id: int) -> dict[str, Any] | None:
        return self._call(self._http.get_json(f"/api/note/{note_id}"))

    @staticmethod
    def _call(raw: Any) -> Any:
        if raw is None:
            return None
        if isinstance(raw, dict) and "code" in raw:
            envelope = ApiEnvelope.model_validate(raw)
            if envelope.code != 0:
                raise ContentClientError(envelope.message or f"code={envelope.code}")
            return envelope.data
        return raw
