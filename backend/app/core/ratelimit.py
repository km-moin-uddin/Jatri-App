"""Fixed-window rate limits, counted in Redis (or memory in dev/tests).

Keyed by client IP for anonymous endpoints (OTP, search) and by user for actions that cost money
or could be brute-forced (holds, payments, promo codes). Behind a reverse proxy, run uvicorn with
`--proxy-headers --forwarded-allow-ips=<proxy ip>` so the real client IP is used.
"""

from collections.abc import Callable
from typing import Annotated

from fastapi import Depends, Header, Request

from app.api.deps import AppSettings
from app.core.envelope import AppError
from app.core.security import decode_access_token
from app.services.cache import KeyValueStore, get_store


def too_many(retry_after: int) -> AppError:
    err = AppError(
        429,
        "rate_limited",
        "Too many requests. Please wait a little and try again.",
        "অনেকবার চেষ্টা করা হয়েছে। একটু পরে আবার চেষ্টা করুন।",
    )
    err.headers = {"Retry-After": str(retry_after)}
    return err


def limit(name: str, times: int, seconds: int, per: str = "ip") -> Callable:
    """A dependency allowing [times] requests per [seconds] for each IP (per="ip") or signed-in
    user (per="user"; falls back to the IP when there's no valid token)."""

    async def dep(
        request: Request,
        settings: AppSettings,
        store: Annotated[KeyValueStore, Depends(get_store)],
        authorization: Annotated[str | None, Header()] = None,
    ) -> None:
        if not settings.rate_limits:
            return
        who = None
        if per == "user" and authorization and authorization.lower().startswith("bearer "):
            uid = decode_access_token(authorization[7:].strip(), settings)
            who = f"u:{uid}" if uid else None
        if who is None:
            who = f"ip:{request.client.host if request.client else 'unknown'}"
        count = await store.hit(f"jatri:rl:{name}:{who}", seconds)
        if count > times:
            raise too_many(seconds)

    return Depends(dep)
