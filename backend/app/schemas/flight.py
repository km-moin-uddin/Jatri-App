from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, EmailStr, Field, model_validator

Cabin = Literal["economy", "premium_economy", "business"]


class SliceIn(BaseModel):
    from_id: str = Field(max_length=40, examples=["air_DAC"])
    to_id: str = Field(max_length=40, examples=["air_CXB"])
    date: date


class FlightSearchIn(BaseModel):
    """One slice = one-way, two (A→B, B→A) = round trip, up to four = multi-city."""

    slices: list[SliceIn] = Field(min_length=1, max_length=4)
    adults: int = Field(default=1, ge=1, le=9)
    children: int = Field(default=0, ge=0, le=8)
    infants: int = Field(default=0, ge=0, le=4)
    cabin: Cabin = "economy"

    @model_validator(mode="after")
    def _check(self):
        if self.adults + self.children > 9:
            raise ValueError("at most 9 passengers with a seat")
        if self.infants > self.adults:
            raise ValueError("each infant travels on an adult's lap")
        if any(b.date < a.date for a, b in zip(self.slices, self.slices[1:], strict=False)):
            raise ValueError("journeys must be in date order")
        return self


class FlightLegOut(BaseModel):
    carrier_code: str
    carrier_name: str
    carrier_logo_url: str | None = None
    flight_number: str
    aircraft: str
    from_id: str
    to_id: str
    from_code: str
    to_code: str
    depart_at: datetime = Field(description="Local time at the departure airport (with its UTC offset)")
    arrive_at: datetime = Field(description="Local time at the arrival airport (with its UTC offset)")
    duration_minutes: int


class FlightSliceOut(BaseModel):
    from_id: str
    to_id: str
    depart_at: datetime
    arrive_at: datetime
    duration_minutes: int
    stops: int
    legs: list[FlightLegOut]


class FareRulesOut(BaseModel):
    refundable: bool
    refund_fee: int | None = Field(description="Per passenger, paisa; null = not refundable")
    change_fee: int | None = Field(description="Per passenger, paisa, plus any fare difference; null = no changes")
    deadline_hours: int = Field(description="Refunds/changes close this many hours before departure")
    checked_baggage_kg: int
    cabin_baggage_kg: int
    no_show: str = Field(description="What happens on a no-show: forfeit | refund_minus_fee")


class PaxFareOut(BaseModel):
    type: Literal["adult", "child", "infant"]
    count: int
    base: int
    tax: int


class FlightOfferOut(BaseModel):
    id: str
    slices: list[FlightSliceOut]
    cabin: Cabin
    fare_brand: Literal["saver", "flex", "business"]
    rules: FareRulesOut
    fares: list[PaxFareOut]
    base_total: int
    tax_total: int
    service_charge: int
    total: int = Field(description="Everything the customer pays, paisa")
    seats_left: int
    international: bool = Field(description="True = passport number + expiry needed for every passenger")


class FlightPassengerIn(BaseModel):
    type: Literal["adult", "child", "infant"]
    # As printed on the passport / NID, Latin letters (airlines can't issue tickets in Bangla).
    given_name: str = Field(min_length=1, max_length=40, pattern=r"^[A-Za-z][A-Za-z .'-]*$")
    surname: str = Field(min_length=1, max_length=40, pattern=r"^[A-Za-z][A-Za-z .'-]*$")
    gender: Literal["male", "female"]
    date_of_birth: date
    nationality: str = Field(default="BD", pattern=r"^[A-Z]{2}$")
    # International: passport number (required, with expiry). Domestic: optional NID or passport.
    document_no: str | None = Field(default=None, pattern=r"^[A-Z0-9]{6,9}$|^\d{10}$|^\d{13}$|^\d{17}$")
    document_expiry: date | None = None


class FlightBookingIn(BaseModel):
    mode: Literal["flight"]
    offer_id: str = Field(max_length=200)
    passengers: list[FlightPassengerIn] = Field(min_length=1, max_length=13)
    contact_phone: str = Field(min_length=10, max_length=20)
    contact_email: EmailStr | None = None
    expected_total: int | None = Field(
        default=None, description="Total (paisa) the app showed; a different live price returns price_changed"
    )
