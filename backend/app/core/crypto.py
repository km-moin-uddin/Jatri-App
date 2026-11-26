"""Encryption at rest for personal documents (NID, passport). Values are never logged."""

import base64
import hashlib
from functools import lru_cache

from cryptography.fernet import Fernet, InvalidToken
from sqlalchemy import String
from sqlalchemy.types import TypeDecorator

from app.core.config import get_settings


@lru_cache
def _fernet() -> Fernet:
    settings = get_settings()
    key = settings.data_key
    if not key:  # dev/test only: production refuses to start without JATRI_DATA_KEY
        key = base64.urlsafe_b64encode(hashlib.sha256(("data:" + settings.secret_key).encode()).digest()).decode()
    return Fernet(key)


class EncryptedString(TypeDecorator):
    """A string column stored as Fernet ciphertext (AES-128-CBC + HMAC)."""

    impl = String(512)
    cache_ok = True

    def process_bind_param(self, value, dialect):
        if value is None:
            return None
        return _fernet().encrypt(value.encode()).decode()

    def process_result_value(self, value, dialect):
        if value is None:
            return None
        try:
            return _fernet().decrypt(value.encode()).decode()
        except InvalidToken:
            return None


def mask_document(value: str | None) -> str | None:
    """`1234567890` -> `••••••7890` for showing back to the user."""
    if not value:
        return None
    return "•" * max(0, len(value) - 4) + value[-4:]
