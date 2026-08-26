"""推荐排序请求 / 响应模型。"""

from __future__ import annotations

from pydantic import BaseModel, Field


class RankRequest(BaseModel):
    user_id: int | None = None
    candidate_note_ids: list[int] = Field(default_factory=list, min_length=0)


class RankResponse(BaseModel):
    note_ids: list[int] = Field(default_factory=list)

    @classmethod
    def identity(cls, request: RankRequest) -> RankResponse:
        return cls(note_ids=list(request.candidate_note_ids))
