"""offers & promo redemptions (with seed offers), saved travellers, price alerts

Revision ID: 0006
Revises: 0005
Create Date: 2026-11-27
"""

import uuid

import sqlalchemy as sa

from alembic import op
from app.seed.offers import seed_offers

revision = "0006"
down_revision = "0005"
branch_labels = None
depends_on = None


def _timestamps() -> list[sa.Column]:
    return [
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    ]


def upgrade() -> None:
    offers = op.create_table(
        "offers",
        sa.Column("id", sa.Uuid, primary_key=True),
        sa.Column("code", sa.String(20), nullable=False),
        sa.Column("title_en", sa.String(120), nullable=False),
        sa.Column("title_bn", sa.String(160), nullable=False),
        sa.Column("subtitle_en", sa.String(200), nullable=False),
        sa.Column("subtitle_bn", sa.String(300), nullable=False),
        sa.Column("discount_type", sa.String(10), nullable=False),
        sa.Column("value", sa.BigInteger, nullable=False),
        sa.Column("max_discount", sa.BigInteger, nullable=True),
        sa.Column("min_amount", sa.BigInteger, nullable=False),
        sa.Column("modes", sa.JSON, nullable=False),
        sa.Column("methods", sa.JSON, nullable=False),
        sa.Column("first_booking_only", sa.Boolean, nullable=False),
        sa.Column("per_user_limit", sa.Integer, nullable=False),
        sa.Column("usage_limit", sa.Integer, nullable=True),
        sa.Column("used_count", sa.Integer, nullable=False),
        sa.Column("valid_from", sa.Date, nullable=False),
        sa.Column("valid_to", sa.Date, nullable=False),
        sa.Column("is_active", sa.Boolean, nullable=False),
        *_timestamps(),
    )
    op.create_index("ix_offers_code", "offers", ["code"], unique=True)
    op.bulk_insert(offers, [{"id": uuid.uuid4(), **o} for o in seed_offers()])

    op.create_table(
        "promo_redemptions",
        sa.Column("id", sa.Uuid, primary_key=True),
        sa.Column("offer_id", sa.Uuid, sa.ForeignKey("offers.id", ondelete="CASCADE"), nullable=False),
        sa.Column("user_id", sa.Uuid, sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("booking_id", sa.Uuid, sa.ForeignKey("bookings.id", ondelete="CASCADE"), nullable=False),
        sa.Column("amount", sa.BigInteger, nullable=False),
        sa.Column("status", sa.String(10), nullable=False),
        *_timestamps(),
        sa.UniqueConstraint("booking_id", name="uq_promo_redemptions_booking"),
    )
    op.create_index("ix_promo_redemptions_offer_id", "promo_redemptions", ["offer_id"])
    op.create_index("ix_promo_redemptions_user_id", "promo_redemptions", ["user_id"])
    op.create_index("ix_promo_redemptions_status", "promo_redemptions", ["status"])

    op.create_table(
        "saved_travellers",
        sa.Column("id", sa.Uuid, primary_key=True),
        sa.Column("user_id", sa.Uuid, sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("full_name", sa.String(80), nullable=False),
        sa.Column("given_name", sa.String(40), nullable=True),
        sa.Column("surname", sa.String(40), nullable=True),
        sa.Column("gender", sa.String(10), nullable=False),
        sa.Column("date_of_birth", sa.Date, nullable=True),
        sa.Column("nationality", sa.String(2), nullable=False),
        sa.Column("nid_number", sa.String(512), nullable=True),
        sa.Column("passport_no", sa.String(512), nullable=True),
        sa.Column("passport_expiry", sa.Date, nullable=True),
        *_timestamps(),
    )
    op.create_index("ix_saved_travellers_user_id", "saved_travellers", ["user_id"])

    op.create_table(
        "price_alerts",
        sa.Column("id", sa.Uuid, primary_key=True),
        sa.Column("user_id", sa.Uuid, sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("mode", sa.String(10), nullable=False),
        sa.Column("from_id", sa.String(40), nullable=False),
        sa.Column("to_id", sa.String(40), nullable=False),
        sa.Column("date", sa.Date, nullable=False),
        sa.Column("target_price", sa.BigInteger, nullable=False),
        sa.Column("last_price", sa.BigInteger, nullable=True),
        sa.Column("notified_price", sa.BigInteger, nullable=True),
        sa.Column("is_active", sa.Boolean, nullable=False),
        *_timestamps(),
    )
    op.create_index("ix_price_alerts_user_id", "price_alerts", ["user_id"])
    op.create_index("ix_price_alerts_is_active", "price_alerts", ["is_active"])


def downgrade() -> None:
    op.drop_table("price_alerts")
    op.drop_table("saved_travellers")
    op.drop_table("promo_redemptions")
    op.drop_table("offers")
