"""payments, tickets

Revision ID: 0003
Revises: 0002
Create Date: 2026-11-26
"""

import sqlalchemy as sa

from alembic import op

revision = "0003"
down_revision = "0002"
branch_labels = None
depends_on = None


def _timestamps() -> list[sa.Column]:
    return [
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    ]


def upgrade() -> None:
    op.create_table(
        "payments",
        sa.Column("id", sa.Uuid, primary_key=True),
        sa.Column("booking_id", sa.Uuid, sa.ForeignKey("bookings.id", ondelete="CASCADE"), nullable=False),
        sa.Column("gateway", sa.String(20), nullable=False),
        sa.Column("method", sa.String(20), nullable=False),
        sa.Column("amount", sa.BigInteger, nullable=False),
        sa.Column("status", sa.String(16), nullable=False),
        sa.Column("gateway_session", sa.String(120)),
        sa.Column("gateway_txn_id", sa.String(120)),
        sa.Column("failure_reason", sa.String(60)),
        sa.Column("raw_response", sa.JSON, nullable=False),
        sa.Column("idempotency_key", sa.String(64)),
        *_timestamps(),
        sa.UniqueConstraint("booking_id", "idempotency_key", name="uq_payments_booking_idempotency"),
    )
    op.create_index("ix_payments_booking_id", "payments", ["booking_id"])
    op.create_index("ix_payments_status", "payments", ["status"])
    op.create_index("ix_payments_gateway_txn_id", "payments", ["gateway_txn_id"])

    op.create_table(
        "tickets",
        sa.Column("id", sa.Uuid, primary_key=True),
        sa.Column("booking_id", sa.Uuid, sa.ForeignKey("bookings.id", ondelete="CASCADE"), nullable=False, unique=True),
        sa.Column("qr_payload", sa.String(200), nullable=False),
        sa.Column("pdf_url", sa.String(200), nullable=False),
        sa.Column("issued_at", sa.DateTime(timezone=True), nullable=False),
        *_timestamps(),
    )


def downgrade() -> None:
    op.drop_table("tickets")
    op.drop_table("payments")
