"""Redis 客户端：热门榜与用户行为特征缓存。"""

from __future__ import annotations

import logging

import redis

from firefly_ai_common.config import FireflyAISettings, load_settings

log = logging.getLogger(__name__)

HOT_NOTES_SUFFIX = "recommend:hot_notes"


class RedisClient:
    def __init__(
        self,
        *,
        host: str,
        port: int,
        db: int = 0,
        key_prefix: str = "firefly:ai",
        client: redis.Redis | None = None,
    ) -> None:
        self._key_prefix = key_prefix.rstrip(":")
        self._owns_client = client is None
        self._client = client or redis.Redis(
            host=host,
            port=port,
            db=db,
            decode_responses=True,
        )

    @classmethod
    def from_settings(cls, settings: FireflyAISettings | None = None) -> RedisClient:
        cfg = settings or load_settings()
        return cls(
            host=cfg.redis_host,
            port=cfg.redis_port,
            db=cfg.redis_db,
            key_prefix=cfg.redis_key_prefix,
        )

    @property
    def raw(self) -> redis.Redis:
        return self._client

    def close(self) -> None:
        if self._owns_client:
            self._client.close()

    def __enter__(self) -> RedisClient:
        return self

    def __exit__(self, *args: object) -> None:
        self.close()

    def _key(self, suffix: str) -> str:
        return f"{self._key_prefix}:{suffix}"

    def hot_notes_key(self) -> str:
        return self._key(HOT_NOTES_SUFFIX)

    def get_hot_note_ids(self, limit: int = 100) -> list[int]:
        raw = self._client.zrevrange(self.hot_notes_key(), 0, max(limit - 1, 0))
        out: list[int] = []
        for item in raw:
            try:
                out.append(int(item))
            except (TypeError, ValueError):
                log.warning("忽略非法 hot note id: %s", item)
        return out

    def upsert_hot_note(self, note_id: int, score: float) -> None:
        self._client.zadd(self.hot_notes_key(), {str(note_id): score})

    def remove_hot_note(self, note_id: int) -> None:
        self._client.zrem(self.hot_notes_key(), str(note_id))

    def ping(self) -> bool:
        return bool(self._client.ping())
