"""assistant-service 配置（LLM / 网络搜索）。"""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


def _split_csv(value: str | None) -> frozenset[str]:
    if not value or not str(value).strip():
        return frozenset()
    parts = str(value).replace(";", ",").split(",")
    return frozenset(p.strip().lower() for p in parts if p and p.strip())


class AssistantSettings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=(".env", "../.env"),
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    llm_base_url: str = "https://api.openai.com/v1"
    llm_api_key: str = ""
    llm_model: str = "gpt-4o-mini"
    llm_timeout_sec: float = 45.0
    llm_max_tokens: int = 280
    llm_enable_thinking: bool = False

    web_search_enabled: bool = True
    web_search_max_results: int = 3
    web_search_timeout_sec: float = 3.0
    # 追加拦截：逗号分隔字符串（避免 pydantic-settings 按 JSON 解析集合）
    web_blocked_domains: str = ""
    web_blocked_keywords: str = ""

    note_search_size: int = 3
    note_snippet_max: int = 100

    @property
    def web_blocked_domain_set(self) -> frozenset[str]:
        return _split_csv(self.web_blocked_domains)

    @property
    def web_blocked_keyword_set(self) -> frozenset[str]:
        return _split_csv(self.web_blocked_keywords)


@lru_cache
def load_assistant_settings() -> AssistantSettings:
    return AssistantSettings()
