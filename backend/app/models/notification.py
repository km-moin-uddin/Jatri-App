import uuid
from datetime import datetime

from sqlalchemy import JSON, Boolean, DateTime, ForeignKey, String
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base, IdMixin


class Notification(IdMixin, Base):
    """One message in the user's inbox (also sent as a push when they have a device registered)."""

    __tablename__ = "notifications"

    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    # booking_confirmed | payment_failed | booking_failed | booking_cancelled | refund_processed |
    # journey_reminder | flight_changed
    type: Mapped[str] = mapped_column(String(30))
    title_en: Mapped[str] = mapped_column(String(120))
    title_bn: Mapped[str] = mapped_column(String(160))
    body_en: Mapped[str] = mapped_column(String(400))
    body_bn: Mapped[str] = mapped_column(String(600))
    data: Mapped[dict] = mapped_column(JSON, default=dict)  # e.g. {"booking_id": ...} for deep links
    is_read: Mapped[bool] = mapped_column(Boolean, default=False, index=True)
    read_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))


class DeviceToken(IdMixin, Base):
    """A phone that can receive push messages (FCM registration token)."""

    __tablename__ = "device_tokens"

    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    token: Mapped[str] = mapped_column(String(255), unique=True)
    platform: Mapped[str] = mapped_column(String(10), default="android")
