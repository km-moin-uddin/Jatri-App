"""refunds, notifications, device tokens

Revision ID: 0005
Revises: 0004
Create Date: 2026-11-27
"""

import sqlalchemy as sa

from alembic import op

revision = "0005"
down_revision = "0004"
branch_labels = None
depends_on = None


def _timestamps() -> list[sa.Column]:
    return [
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    ]


def upgrade() -> None:
    op.create_table(
        "refunds",
        sa.Column("id", sa.Uuid, primary_key=True),
        sa.Column("booking_id", sa.Uuid, sa.ForeignKey("bookings.id", ondelete="CASCADE"), nullable=False),
        sa.Column("payment_id", sa.Uuid, sa.ForeignKey("payments.id", ondelete="CASCADE"), nullable=False),
        sa.Column("amount", sa.BigInteger, nullable=False),
        sa.Column("fee", sa.BigInteger, nullable=False),
        sa.Column("reason", sa.String(30), nullable=False),
        sa.Column("status", sa.String(16), nullable=False),
        sa.Column("gateway_ref", sa.String(120), nullable=True),
        sa.Column("attempts", sa.Integer, nullable=False),
        sa.Column("processed_at", sa.DateTime(timezone=True), nullable=True),
        *_timestamps(),
    )
    op.create_index("ix_refunds_booking_id", "refunds", ["booking_id"])
    op.create_index("ix_refunds_payment_id", "refunds", ["payment_id"])
    op.create_index("ix_refunds_status", "refunds", ["status"])

    op.create_table(
        "notifications",
        sa.Column("id", sa.Uuid, primary_key=True),
        sa.Column("user_id", sa.Uuid, sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("type", sa.String(30), nullable=False),
        sa.Column("title_en", sa.String(120), nullable=False),
        sa.Column("title_bn", sa.String(160), nullable=False),
        sa.Column("body_en", sa.String(400), nullable=False),
        sa.Column("body_bn", sa.String(600), nullable=False),
        sa.Column("data", sa.JSON, nullable=False),
        sa.Column("is_read", sa.Boolean, nullable=False),
        sa.Column("read_at", sa.DateTime(timezone=True), nullable=True),
        *_timestamps(),
    )
    op.create_index("ix_notifications_user_id", "notifications", ["user_id"])
    op.create_index("ix_notifications_is_read", "notifications", ["is_read"])

    op.create_table(
        "device_tokens",
        sa.Column("id", sa.Uuid, primary_key=True),
        sa.Column("user_id", sa.Uuid, sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("token", sa.String(255), nullable=False, unique=True),
        sa.Column("platform", sa.String(10), nullable=False),
        *_timestamps(),
    )
    op.create_index("ix_device_tokens_user_id", "device_tokens", ["user_id"])


def downgrade() -> None:
    op.drop_table("device_tokens")
    op.drop_table("notifications")
    op.drop_table("refunds")
