from sqlalchemy import Boolean, String
from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base, IdMixin


class Location(IdMixin, Base):
    """Airport, bus city or train station. `key` is the stable id the app uses (e.g. air_DAC)."""

    __tablename__ = "locations"

    key: Mapped[str] = mapped_column(String(40), unique=True)
    type: Mapped[str] = mapped_column(String(10), index=True)  # airport | bus_city | station
    code: Mapped[str] = mapped_column(String(20), index=True)
    name_en: Mapped[str] = mapped_column(String(120))
    name_bn: Mapped[str] = mapped_column(String(120))
    city_en: Mapped[str] = mapped_column(String(80))
    city_bn: Mapped[str] = mapped_column(String(80))
    country: Mapped[str] = mapped_column(String(2), default="BD")
    is_popular: Mapped[bool] = mapped_column(Boolean, default=False)
