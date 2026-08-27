"""环境变量与全局配置。"""

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class FireflyAISettings(BaseSettings):
    """与 services-ai/.env.example 及 deploy/docker-compose.ai.yml 对齐。"""

    model_config = SettingsConfigDict(
        env_file=(".env", "../.env"),
        env_file_encoding="utf-8",
        extra="ignore",
        case_sensitive=False,
    )

    rabbitmq_host: str = "127.0.0.1"
    rabbitmq_port: int = 5672
    rabbitmq_user: str = "guest"
    rabbitmq_password: str = ""

    internal_hmac_secret: str = ""
    internal_auth_enabled: bool = True
    hmac_skew_sec: int = 60

    content_base_url: str = "http://127.0.0.1:9002"
    media_base_url: str = "http://127.0.0.1:9003"
    search_base_url: str = "http://127.0.0.1:9007"

    http_connect_timeout_sec: float = 2.0
    http_read_timeout_sec: float = 3.0

    log_level: str = "INFO"


@lru_cache
def load_settings() -> FireflyAISettings:
    return FireflyAISettings()
