"""推荐排序请求 / 响应模型（骨架）。"""

from pydantic import BaseModel


class RankRequest(BaseModel):
    user_id: int | None = None
    candidate_note_ids: list[int]


class RankResponse(BaseModel):
    note_ids: list[int]
