"""Tokens and hashing. OTPs and refresh tokens are stored only as HMAC hashes."""

import hashlib
import hmac
import secrets
import uuid
from datetime import UTC, datetime, timedelta

import jwt

from app.core.config import Settings

ALGORITHM = "HS256"


def now() -> datetime:
    return datetime.now(UTC)


def hmac_hash(value: str, settings: Settings) -> str:
    return hmac.new(settings.secret_key.encode(), value.encode(), hashlib.sha256).hexdigest()


def generate_otp(settings: Settings) -> str:
    if settings.dev_fixed_otp:
        return settings.dev_fixed_otp
    return "".join(secrets.choice("0123456789") for _ in range(settings.otp_length))


def create_access_token(user_id: uuid.UUID, settings: Settings) -> tuple[str, int]:
    expires_in = settings.access_token_minutes * 60
    payload = {
        "sub": str(user_id),
        "type": "access",
        "iat": int(now().timestamp()),
        "exp": int((now() + timedelta(seconds=expires_in)).timestamp()),
    }
    return jwt.encode(payload, settings.secret_key, algorithm=ALGORITHM), expires_in


def decode_access_token(token: str, settings: Settings) -> uuid.UUID | None:
    try:
        payload = jwt.decode(token, settings.secret_key, algorithms=[ALGORITHM])
    except jwt.PyJWTError:
        return None
    if payload.get("type") != "access":
        return None
    try:
        return uuid.UUID(payload["sub"])
    except (KeyError, ValueError):
        return None


def new_refresh_token() -> str:
    return secrets.token_urlsafe(48)
