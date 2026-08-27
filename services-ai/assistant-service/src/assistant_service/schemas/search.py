"""搜索问答 API 模型。"""

from pydantic import BaseModel, ConfigDict, Field


class NoteCandidate(BaseModel):
    """前端已检索的笔记（避免 assistant 重复调 search-service）。"""

    model_config = ConfigDict(populate_by_name=True)

    id: int
    title: str | None = None
    content: str | None = None
    cover_url: str | None = Field(default=None, alias="coverUrl")


class SearchRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    query: str = Field(min_length=1, max_length=500)
    include_web: bool = Field(default=True, alias="includeWeb")
    note_limit: int = Field(default=3, ge=1, le=10, alias="noteLimit")
    note_candidates: list[NoteCandidate] = Field(default_factory=list, alias="noteCandidates")


class NoteSource(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    id: int
    title: str | None = None
    snippet: str | None = None
    cover_url: str | None = Field(default=None, alias="coverUrl")


class WebSource(BaseModel):
    title: str
    url: str
    snippet: str | None = None


class SearchData(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    answer: str
    note_sources: list[NoteSource] = Field(default_factory=list, alias="noteSources")
    web_sources: list[WebSource] = Field(default_factory=list, alias="webSources")


class ApiResponse(BaseModel):
    code: int
    message: str
    data: SearchData | None = None

    @staticmethod
    def ok(data: SearchData) -> "ApiResponse":
        return ApiResponse(code=0, message="ok", data=data)

    @staticmethod
    def fail(code: int, message: str) -> "ApiResponse":
        return ApiResponse(code=code, message=message, data=None)
