"""note.created 事件模型（字段对齐 Java NoteIndexEvent）。"""

from pydantic import BaseModel, ConfigDict, Field


class NoteCreatedEvent(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: int | None = None
    user_id: int | None = Field(default=None, alias="userId")
    title: str | None = None
    content: str | None = None
    cover_url: str | None = Field(default=None, alias="coverUrl")
    media_urls: list[str] = Field(default_factory=list, alias="mediaUrls")

    def is_valid(self) -> bool:
        return self.id is not None

    def image_paths(self) -> list[str]:
        """封面 + 正文附图，去重并保持顺序。"""
        ordered: list[str] = []
        seen: set[str] = set()
        for raw in [self.cover_url, *(self.media_urls or [])]:
            if not raw:
                continue
            path = raw.strip()
            if not path or path in seen:
                continue
            seen.add(path)
            ordered.append(path)
        return ordered
