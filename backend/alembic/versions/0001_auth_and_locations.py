"""users, auth_otps, refresh_tokens, locations (+ seed locations)

Revision ID: 0001
Revises:
Create Date: 2026-11-26
"""

import uuid

import sqlalchemy as sa

from alembic import op
from app.seed import load_locations

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def _timestamps() -> list[sa.Column]:
    return [
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    ]


def upgrade() -> None:
    op.create_table(
        "users",
        sa.Column("id", sa.Uuid, primary_key=True),
        sa.Column("phone", sa.String(11), nullable=False),
        sa.Column("name", sa.String(80)),
        sa.Column("email", sa.String(254)),
        sa.Column("gender", sa.String(10)),
        sa.Column("dob", sa.Date),
        sa.Column("language", sa.String(2), nullable=False, server_default="bn"),
        sa.Column("avatar_url", sa.String(500)),
        sa.Column("status", sa.String(16), nullable=False, server_default="active"),
        *_timestamps(),
    )
    op.create_index("ix_users_phone", "users", ["phone"], unique=True)

    op.create_table(
        "auth_otps",
        sa.Column("id", sa.Uuid, primary_key=True),
        sa.Column("phone", sa.String(11), nullable=False),
        sa.Column("otp_hash", sa.String(64), nullable=False),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("attempts", sa.Integer, nullable=False, server_default="0"),
        sa.Column("consumed", sa.Boolean, nullable=False, server_default=sa.false()),
        *_timestamps(),
    )
    op.create_index("ix_auth_otps_phone", "auth_otps", ["phone"])

    op.create_table(
        "refresh_tokens",
        sa.Column("id", sa.Uuid, primary_key=True),
        sa.Column("user_id", sa.Uuid, sa.ForeignKey("users.id", ondelete="CASCADE"), nullable=False),
        sa.Column("token_hash", sa.String(64), nullable=False),
        sa.Column("device_info", sa.String(200)),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("revoked", sa.Boolean, nullable=False, server_default=sa.false()),
        *_timestamps(),
    )
    op.create_index("ix_refresh_tokens_user_id", "refresh_tokens", ["user_id"])
    op.create_index("ix_refresh_tokens_token_hash", "refresh_tokens", ["token_hash"], unique=True)

    locations = op.create_table(
        "locations",
        sa.Column("id", sa.Uuid, primary_key=True),
        sa.Column("key", sa.String(40), nullable=False, unique=True),
        sa.Column("type", sa.String(10), nullable=False),
        sa.Column("code", sa.String(20), nullable=False),
        sa.Column("name_en", sa.String(120), nullable=False),
        sa.Column("name_bn", sa.String(120), nullable=False),
        sa.Column("city_en", sa.String(80), nullable=False),
        sa.Column("city_bn", sa.String(80), nullable=False),
        sa.Column("country", sa.String(2), nullable=False, server_default="BD"),
        sa.Column("is_popular", sa.Boolean, nullable=False, server_default=sa.false()),
        *_timestamps(),
    )
    op.create_index("ix_locations_type", "locations", ["type"])
    op.create_index("ix_locations_code", "locations", ["code"])
    op.bulk_insert(locations, [{"id": uuid.uuid4(), **row} for row in load_locations()])


def downgrade() -> None:
    op.drop_table("locations")
    op.drop_table("refresh_tokens")
    op.drop_table("auth_otps")
    op.drop_table("users")
