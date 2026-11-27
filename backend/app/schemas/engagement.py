import uuid
from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, Field, model_validator


class OfferOut(BaseModel):
    id: uuid.UUID
    code: str
    title_en: str
    title_bn: str
    subtitle_en: str
    subtitle_bn: str
    discount_type: Literal["flat", "percent"]
    value: int = Field(description="Flat: paisa. Percent: whole percent")
    max_discount: int | None
    min_amount: int
    modes: list[str] = Field(description="Empty = every mode")
    methods: list[str] = Field(description="Payment methods the code needs; empty = any")
    first_booking_only: bool
    valid_to: date


class PromoIn(BaseModel):
    code: str = Field(min_length=3, max_length=20)


class TravellerIn(BaseModel):
    full_name: str = Field(min_length=2, max_length=80, description="As on the NID (bus, train)")
    given_name: str | None = Field(default=None, max_length=40, pattern=r"^[A-Za-z][A-Za-z .'-]*$")
    surname: str | None = Field(default=None, max_length=40, pattern=r"^[A-Za-z][A-Za-z .'-]*$")
    gender: Literal["male", "female", "other"]
    date_of_birth: date | None = None
    nationality: str = Field(default="BD", pattern=r"^[A-Z]{2}$")
    nid_number: str | None = Field(default=None, pattern=r"^\d{10}$|^\d{13}$|^\d{17}$")
    passport_no: str | None = Field(default=None, pattern=r"^[A-Z0-9]{6,9}$")
    passport_expiry: date | None = None

    @model_validator(mode="after")
    def _check(self):
        if self.date_of_birth and self.date_of_birth > date.today():
            raise ValueError("date of birth is in the future")
        if self.passport_no and not self.passport_expiry:
            raise ValueError("passport expiry is needed with a passport number")
        return self


class TravellerOut(TravellerIn):
    """The owner gets the full ID numbers back to fill passenger forms (encrypted at rest)."""

    id: uuid.UUID
    type: Literal["adult", "child", "infant"] | None = Field(description="From the date of birth, today")


class PriceAlertIn(BaseModel):
    mode: Literal["flight", "bus", "train"]
    from_id: str = Field(max_length=40)
    to_id: str = Field(max_length=40)
    date: date
    target_price: int = Field(gt=0, description="Paisa per person; alert when the lowest fare is at or below it")

    @model_validator(mode="after")
    def _check(self):
        if self.from_id == self.to_id:
            raise ValueError("from and to are the same")
        prefix = {"flight": "air_", "bus": "bus_", "train": "stn_"}[self.mode]
        if not (self.from_id.startswith(prefix) and self.to_id.startswith(prefix)):
            raise ValueError("places don't match the mode")
        return self


class PriceAlertOut(BaseModel):
    id: uuid.UUID
    mode: str
    from_id: str
    to_id: str
    date: date
    target_price: int
    last_price: int | None = Field(description="Lowest fare per person at the last check")
    is_active: bool
    created_at: datetime
