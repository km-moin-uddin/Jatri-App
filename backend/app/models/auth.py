import uuid
from datetime import datetime

from sqlalchemy import Boolean, DateTime, ForeignKey, Integer, String
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base, IdMixin


class AuthOtp(IdMixin, Base):
    """One OTP sent to a phone. Only the HMAC hash of the code is stored."""

    __tablename__ = "auth_otps"

    phone: Mapped[str] = mapped_column(String(11), index=True)
    otp_hash: Mapped[str] = mapped_column(String(64))
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    attempts: Mapped[int] = mapped_column(Integer, default=0)
    consumed: Mapped[bool] = mapped_column(Boolean, default=False)


class RefreshToken(IdMixin, Base):
    """A refresh token (hashed). Rotated on every use; revoked on logout."""

    __tablename__ = "refresh_tokens"

    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True, index=True)
    device_info: Mapped[str | None] = mapped_column(String(200))
    expires_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    revoked: Mapped[bool] = mapped_column(Boolean, default=False)
