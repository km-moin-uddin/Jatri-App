"""Offers & promo codes, saved travellers and price alerts (Phase 8)."""

import uuid
from datetime import date

from sqlalchemy import JSON, BigInteger, Boolean, Date, ForeignKey, Integer, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column

from app.core.crypto import EncryptedString
from app.db.base import Base, IdMixin


class Offer(IdMixin, Base):
    """A promo code. Money in paisa; percent offers keep `value` as a whole percent."""

    __tablename__ = "offers"

    code: Mapped[str] = mapped_column(String(20), unique=True, index=True)
    title_en: Mapped[str] = mapped_column(String(120))
    title_bn: Mapped[str] = mapped_column(String(160))
    subtitle_en: Mapped[str] = mapped_column(String(200))
    subtitle_bn: Mapped[str] = mapped_column(String(300))
    discount_type: Mapped[str] = mapped_column(String(10))  # flat | percent
    value: Mapped[int] = mapped_column(BigInteger)
    max_discount: Mapped[int | None] = mapped_column(BigInteger)  # cap for percent offers
    min_amount: Mapped[int] = mapped_column(BigInteger, default=0)  # minimum fare to qualify
    modes: Mapped[list[str]] = mapped_column(JSON, default=list)  # empty = every mode
    methods: Mapped[list[str]] = mapped_column(JSON, default=list)  # payment methods; empty = any
    first_booking_only: Mapped[bool] = mapped_column(Boolean, default=False)
    per_user_limit: Mapped[int] = mapped_column(Integer, default=1)
    usage_limit: Mapped[int | None] = mapped_column(Integer)  # all users together; None = unlimited
    used_count: Mapped[int] = mapped_column(Integer, default=0)
    valid_from: Mapped[date] = mapped_column(Date)
    valid_to: Mapped[date] = mapped_column(Date)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)


class PromoRedemption(IdMixin, Base):
    """A code applied to a booking: `applied` while held, `used` once ticketed, `released` if not."""

    __tablename__ = "promo_redemptions"
    __table_args__ = (UniqueConstraint("booking_id", name="uq_promo_redemptions_booking"),)

    offer_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("offers.id", ondelete="CASCADE"), index=True)
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    booking_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("bookings.id", ondelete="CASCADE"))
    amount: Mapped[int] = mapped_column(BigInteger)
    status: Mapped[str] = mapped_column(String(10), index=True)


class SavedTraveller(IdMixin, Base):
    """Family and friends for one-tap passenger forms. ID numbers are encrypted at rest."""

    __tablename__ = "saved_travellers"

    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    full_name: Mapped[str] = mapped_column(String(80))  # as on the NID (bus / train)
    given_name: Mapped[str | None] = mapped_column(String(40))  # as on the passport (flights)
    surname: Mapped[str | None] = mapped_column(String(40))
    gender: Mapped[str] = mapped_column(String(10))
    date_of_birth: Mapped[date | None] = mapped_column(Date)
    nationality: Mapped[str] = mapped_column(String(2), default="BD")
    nid_number: Mapped[str | None] = mapped_column(EncryptedString)
    passport_no: Mapped[str | None] = mapped_column(EncryptedString)
    passport_expiry: Mapped[date | None] = mapped_column(Date)


class PriceAlert(IdMixin, Base):
    """ "Tell me when Dhaka → Cox's Bazar on 12 Dec drops to ৳1,200." Checked by a background job."""

    __tablename__ = "price_alerts"

    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    mode: Mapped[str] = mapped_column(String(10))
    from_id: Mapped[str] = mapped_column(String(40))
    to_id: Mapped[str] = mapped_column(String(40))
    date: Mapped[date] = mapped_column(Date)
    target_price: Mapped[int] = mapped_column(BigInteger)  # paisa, per person
    last_price: Mapped[int | None] = mapped_column(BigInteger)
    notified_price: Mapped[int | None] = mapped_column(BigInteger)  # lowest price we already told them about
    is_active: Mapped[bool] = mapped_column(Boolean, default=True, index=True)
