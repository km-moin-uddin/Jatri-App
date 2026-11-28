"""indexes for the hot queries: My Trips, inbox pages, OTP checks, hold expiry, reminders

Revision ID: 0007
Revises: 0006
Create Date: 2026-11-27
"""

from alembic import op

revision = "0007"
down_revision = "0006"
branch_labels = None
depends_on = None

_INDEXES = [
    ("ix_bookings_user_created", "bookings", ["user_id", "created_at"]),
    ("ix_bookings_status_hold", "bookings", ["status", "hold_expires_at"]),
    ("ix_booking_segments_depart_at", "booking_segments", ["depart_at"]),
    ("ix_notifications_user_created", "notifications", ["user_id", "created_at"]),
    ("ix_auth_otps_phone_created", "auth_otps", ["phone", "created_at"]),
]


def upgrade() -> None:
    for name, table, cols in _INDEXES:
        op.create_index(name, table, cols)


def downgrade() -> None:
    for name, table, _ in reversed(_INDEXES):
        op.drop_index(name, table_name=table)
