"""环境变量与全局配置（骨架，待各服务接入）。"""

from pydantic_settings import BaseSettings, SettingsConfigDict


class FireflyAISettings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    rabbitmq_host: str = "127.0.0.1"
    rabbitmq_port: int = 5672
    rabbitmq_user: str = "guest"
    rabbitmq_password: str = ""

    redis_host: str = "127.0.0.1"
    redis_port: int = 6379

    internal_hmac_secret: str = ""
    content_base_url: str = "http://127.0.0.1:9002"

    log_level: str = "INFO"


def load_settings() -> FireflyAISettings:
    return FireflyAISettings()
