import uuid
from datetime import datetime

from sqlalchemy import JSON, BigInteger, DateTime, ForeignKey, Integer, String, UniqueConstraint
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.core.crypto import EncryptedString
from app.db.base import Base, IdMixin


class Booking(IdMixin, Base):
    """One booking. Money is always integer paisa."""

    __tablename__ = "bookings"
    __table_args__ = (UniqueConstraint("user_id", "idempotency_key", name="uq_bookings_user_idempotency"),)

    booking_ref: Mapped[str] = mapped_column(String(8), unique=True, index=True)
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="SET NULL"), index=True, nullable=True)
    mode: Mapped[str] = mapped_column(String(10))  # flight | bus | train
    provider: Mapped[str] = mapped_column(String(40))
    provider_booking_ref: Mapped[str | None] = mapped_column(String(80))
    # pending | held | confirmed | ticketed | cancelled | refunded | failed
    status: Mapped[str] = mapped_column(String(16), index=True)
    hold_expires_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    base_fare: Mapped[int] = mapped_column(BigInteger)
    service_charge: Mapped[int] = mapped_column(BigInteger, default=0)
    discount: Mapped[int] = mapped_column(BigInteger, default=0)
    total_amount: Mapped[int] = mapped_column(BigInteger)
    currency: Mapped[str] = mapped_column(String(3), default="BDT")
    contact_phone: Mapped[str] = mapped_column(String(11))
    contact_email: Mapped[str | None] = mapped_column(String(254))
    idempotency_key: Mapped[str | None] = mapped_column(String(64))

    segments: Mapped[list["BookingSegment"]] = relationship(
        back_populates="booking", cascade="all, delete-orphan", lazy="selectin", order_by="BookingSegment.depart_at"
    )
    passengers: Mapped[list["BookingPassenger"]] = relationship(
        back_populates="booking", cascade="all, delete-orphan", lazy="selectin", order_by="BookingPassenger.position"
    )


class BookingSegment(IdMixin, Base):
    __tablename__ = "booking_segments"

    booking_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("bookings.id", ondelete="CASCADE"), index=True)
    trip_id: Mapped[str] = mapped_column(String(120))
    carrier: Mapped[str] = mapped_column(String(80))  # airline / bus operator / railway
    number: Mapped[str] = mapped_column(String(40))  # flight no / coach no / train no
    from_id: Mapped[str] = mapped_column(String(40))
    to_id: Mapped[str] = mapped_column(String(40))
    depart_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    arrive_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    travel_class: Mapped[str] = mapped_column(String(20))  # ac / non_ac / economy / snigdha ...
    seat_numbers: Mapped[list[str]] = mapped_column(JSON, default=list)
    baggage: Mapped[str | None] = mapped_column(String(40))
    extra: Mapped[dict] = mapped_column(JSON, default=dict)  # boarding / dropping points etc.

    booking: Mapped[Booking] = relationship(back_populates="segments")


class BookingPassenger(IdMixin, Base):
    __tablename__ = "booking_passengers"

    booking_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("bookings.id", ondelete="CASCADE"), index=True)
    position: Mapped[int] = mapped_column(Integer)
    full_name: Mapped[str] = mapped_column(String(80))
    type: Mapped[str] = mapped_column(String(8))  # adult | child | infant
    gender: Mapped[str] = mapped_column(String(10))
    age: Mapped[int | None] = mapped_column(Integer)
    document_no: Mapped[str | None] = mapped_column(EncryptedString)  # NID/passport, encrypted at rest
    seat_no: Mapped[str | None] = mapped_column(String(10))
    ticket_number: Mapped[str | None] = mapped_column(String(40))

    booking: Mapped[Booking] = relationship(back_populates="passengers")


class Payment(IdMixin, Base):
    """One payment attempt for a booking. Status only moves forward; callbacks are idempotent."""

    __tablename__ = "payments"
    __table_args__ = (UniqueConstraint("booking_id", "idempotency_key", name="uq_payments_booking_idempotency"),)

    booking_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("bookings.id", ondelete="CASCADE"), index=True)
    gateway: Mapped[str] = mapped_column(String(20))
    method: Mapped[str] = mapped_column(String(20))  # bkash | nagad | rocket | card
    amount: Mapped[int] = mapped_column(BigInteger)  # paisa
    # initiated | success | failed | cancelled | refund_pending | refunded
    status: Mapped[str] = mapped_column(String(16), index=True)
    gateway_session: Mapped[str | None] = mapped_column(String(120))
    gateway_txn_id: Mapped[str | None] = mapped_column(String(120), index=True)
    failure_reason: Mapped[str | None] = mapped_column(String(60))
    raw_response: Mapped[dict] = mapped_column(JSON, default=dict)
    idempotency_key: Mapped[str | None] = mapped_column(String(64))


class Ticket(IdMixin, Base):
    __tablename__ = "tickets"

    booking_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("bookings.id", ondelete="CASCADE"), unique=True)
    qr_payload: Mapped[str] = mapped_column(String(200))
    pdf_url: Mapped[str] = mapped_column(String(200))
    issued_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
