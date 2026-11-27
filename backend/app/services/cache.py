"""Search cache and seat-hold locks. Redis in production; an in-process fallback for dev and tests."""

import json
import time
from functools import lru_cache
from typing import Any, Protocol

from redis.asyncio import Redis

from app.core.config import get_settings


class KeyValueStore(Protocol):
    async def get_json(self, key: str) -> Any | None: ...
    async def set_json(self, key: str, value: Any, ttl: int) -> None: ...

    async def acquire_all(self, keys: list[str], owner: str, ttl: int) -> bool:
        """Takes every key for [owner] or none of them (no partial seat holds)."""

    async def release(self, keys: list[str], owner: str) -> None:
        """Releases keys only if [owner] still holds them."""

    async def owners(self, keys: list[str]) -> dict[str, str]: ...

    async def hit(self, key: str, window: int) -> int:
        """Counts one request in a fixed window of [window] seconds; returns the count so far."""


class MemoryStore:
    def __init__(self) -> None:
        self._data: dict[str, tuple[float, str]] = {}

    def _get(self, key: str) -> str | None:
        item = self._data.get(key)
        if item is None:
            return None
        if item[0] < time.monotonic():
            self._data.pop(key, None)
            return None
        return item[1]

    async def get_json(self, key: str) -> Any | None:
        raw = self._get(key)
        return json.loads(raw) if raw is not None else None

    async def set_json(self, key: str, value: Any, ttl: int) -> None:
        self._data[key] = (time.monotonic() + ttl, json.dumps(value))

    async def acquire_all(self, keys: list[str], owner: str, ttl: int) -> bool:
        if any((v := self._get(k)) is not None and v != owner for k in keys):
            return False
        for k in keys:
            self._data[k] = (time.monotonic() + ttl, owner)
        return True

    async def release(self, keys: list[str], owner: str) -> None:
        for k in keys:
            if self._get(k) == owner:
                self._data.pop(k, None)

    async def owners(self, keys: list[str]) -> dict[str, str]:
        return {k: v for k in keys if (v := self._get(k)) is not None}

    async def hit(self, key: str, window: int) -> int:
        item = self._data.get(key)
        if item is None or item[0] < time.monotonic():
            item = (time.monotonic() + window, "0")
        count = int(item[1]) + 1
        self._data[key] = (item[0], str(count))
        return count


# Atomically SET NX every key, or roll back the ones we just set.
_ACQUIRE = """
for i, key in ipairs(KEYS) do
  local cur = redis.call('GET', key)
  if cur and cur ~= ARGV[1] then
    for j = 1, i - 1 do redis.call('DEL', KEYS[j]) end
    return 0
  end
  redis.call('SET', key, ARGV[1], 'EX', ARGV[2])
end
return 1
"""

_RELEASE = """
for _, key in ipairs(KEYS) do
  if redis.call('GET', key) == ARGV[1] then redis.call('DEL', key) end
end
return 1
"""


class RedisStore:
    def __init__(self, url: str) -> None:
        self._redis = Redis.from_url(url, decode_responses=True)
        self._acquire = self._redis.register_script(_ACQUIRE)
        self._release = self._redis.register_script(_RELEASE)

    async def get_json(self, key: str) -> Any | None:
        raw = await self._redis.get(key)
        return json.loads(raw) if raw is not None else None

    async def set_json(self, key: str, value: Any, ttl: int) -> None:
        await self._redis.set(key, json.dumps(value), ex=ttl)

    async def acquire_all(self, keys: list[str], owner: str, ttl: int) -> bool:
        # Note: a real Redis Cluster needs all keys in one hash slot ({trip} tag); a single node is fine.
        return bool(await self._acquire(keys=keys, args=[owner, ttl]))

    async def release(self, keys: list[str], owner: str) -> None:
        await self._release(keys=keys, args=[owner])

    async def owners(self, keys: list[str]) -> dict[str, str]:
        if not keys:
            return {}
        values = await self._redis.mget(keys)
        return {k: v for k, v in zip(keys, values, strict=True) if v is not None}

    async def hit(self, key: str, window: int) -> int:
        async with self._redis.pipeline(transaction=True) as pipe:
            pipe.incr(key)
            pipe.expire(key, window, nx=True)
            count, _ = await pipe.execute()
        return int(count)


@lru_cache
def get_store() -> KeyValueStore:
    url = get_settings().redis_url
    return RedisStore(url) if url else MemoryStore()


def seat_key(trip_id: str, seat: str) -> str:
    return f"jatri:seat:{{{trip_id}}}:{seat}"
