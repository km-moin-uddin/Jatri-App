"""flight bookings: booking details (fare rules), passenger DOB / nationality / passport expiry

Revision ID: 0004
Revises: 0003
Create Date: 2026-11-26
"""

import sqlalchemy as sa

from alembic import op

revision = "0004"
down_revision = "0003"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column("bookings", sa.Column("details", sa.JSON, nullable=False, server_default="{}"))
    op.add_column("booking_passengers", sa.Column("date_of_birth", sa.Date, nullable=True))
    op.add_column("booking_passengers", sa.Column("nationality", sa.String(2), nullable=True))
    op.add_column("booking_passengers", sa.Column("document_expiry", sa.Date, nullable=True))


def downgrade() -> None:
    op.drop_column("booking_passengers", "document_expiry")
    op.drop_column("booking_passengers", "nationality")
    op.drop_column("booking_passengers", "date_of_birth")
    op.drop_column("bookings", "details")
