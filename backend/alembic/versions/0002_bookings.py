"""bookings, booking_segments, booking_passengers

Revision ID: 0002
Revises: 0001
Create Date: 2026-11-26
"""

import sqlalchemy as sa

from alembic import op

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None


def _timestamps() -> list[sa.Column]:
    return [
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    ]


def upgrade() -> None:
    op.create_table(
        "bookings",
        sa.Column("id", sa.Uuid, primary_key=True),
        sa.Column("booking_ref", sa.String(8), nullable=False),
        sa.Column("user_id", sa.Uuid, sa.ForeignKey("users.id", ondelete="SET NULL")),
        sa.Column("mode", sa.String(10), nullable=False),
        sa.Column("provider", sa.String(40), nullable=False),
        sa.Column("provider_booking_ref", sa.String(80)),
        sa.Column("status", sa.String(16), nullable=False),
        sa.Column("hold_expires_at", sa.DateTime(timezone=True)),
        sa.Column("base_fare", sa.BigInteger, nullable=False),
        sa.Column("service_charge", sa.BigInteger, nullable=False, server_default="0"),
        sa.Column("discount", sa.BigInteger, nullable=False, server_default="0"),
        sa.Column("total_amount", sa.BigInteger, nullable=False),
        sa.Column("currency", sa.String(3), nullable=False, server_default="BDT"),
        sa.Column("contact_phone", sa.String(11), nullable=False),
        sa.Column("contact_email", sa.String(254)),
        sa.Column("idempotency_key", sa.String(64)),
        *_timestamps(),
        sa.UniqueConstraint("user_id", "idempotency_key", name="uq_bookings_user_idempotency"),
    )
    op.create_index("ix_bookings_booking_ref", "bookings", ["booking_ref"], unique=True)
    op.create_index("ix_bookings_user_id", "bookings", ["user_id"])
    op.create_index("ix_bookings_status", "bookings", ["status"])
    op.create_index("ix_bookings_user_status", "bookings", ["user_id", "status"])

    op.create_table(
        "booking_segments",
        sa.Column("id", sa.Uuid, primary_key=True),
        sa.Column("booking_id", sa.Uuid, sa.ForeignKey("bookings.id", ondelete="CASCADE"), nullable=False),
        sa.Column("trip_id", sa.String(120), nullable=False),
        sa.Column("carrier", sa.String(80), nullable=False),
        sa.Column("number", sa.String(40), nullable=False),
        sa.Column("from_id", sa.String(40), nullable=False),
        sa.Column("to_id", sa.String(40), nullable=False),
        sa.Column("depart_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("arrive_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("travel_class", sa.String(20), nullable=False),
        sa.Column("seat_numbers", sa.JSON, nullable=False),
        sa.Column("baggage", sa.String(40)),
        sa.Column("extra", sa.JSON, nullable=False),
        *_timestamps(),
    )
    op.create_index("ix_booking_segments_booking_id", "booking_segments", ["booking_id"])

    op.create_table(
        "booking_passengers",
        sa.Column("id", sa.Uuid, primary_key=True),
        sa.Column("booking_id", sa.Uuid, sa.ForeignKey("bookings.id", ondelete="CASCADE"), nullable=False),
        sa.Column("position", sa.Integer, nullable=False),
        sa.Column("full_name", sa.String(80), nullable=False),
        sa.Column("type", sa.String(8), nullable=False),
        sa.Column("gender", sa.String(10), nullable=False),
        sa.Column("age", sa.Integer),
        sa.Column("document_no", sa.String(512)),
        sa.Column("seat_no", sa.String(10)),
        sa.Column("ticket_number", sa.String(40)),
        *_timestamps(),
    )
    op.create_index("ix_booking_passengers_booking_id", "booking_passengers", ["booking_id"])


def downgrade() -> None:
    op.drop_table("booking_passengers")
    op.drop_table("booking_segments")
    op.drop_table("bookings")
