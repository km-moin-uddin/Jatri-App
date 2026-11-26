from datetime import date

from sqlalchemy import Date, String
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base, IdMixin


class User(IdMixin, Base):
    __tablename__ = "users"

    phone: Mapped[str] = mapped_column(String(11), unique=True, index=True)
    name: Mapped[str | None] = mapped_column(String(80))
    email: Mapped[str | None] = mapped_column(String(254))
    gender: Mapped[str | None] = mapped_column(String(10))
    dob: Mapped[date | None] = mapped_column(Date)
    language: Mapped[str] = mapped_column(String(2), default="bn")
    avatar_url: Mapped[str | None] = mapped_column(String(500))
    status: Mapped[str] = mapped_column(String(16), default="active")
    # NID is collected (and encrypted at rest) from Phase 3, with the passenger form.
