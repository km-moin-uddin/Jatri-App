import uuid
from datetime import datetime
from typing import Literal

from pydantic import BaseModel, EmailStr, Field, model_validator


class PassengerIn(BaseModel):
    full_name: str = Field(min_length=2, max_length=80)
    gender: Literal["male", "female", "other"]
    age: int | None = Field(default=None, ge=1, le=120)
    seat_no: str = Field(max_length=10)
    # NID: 10, 13 or 17 digits. Optional for bus, required for train (Phase 6). Encrypted at rest.
    document_no: str | None = Field(default=None, pattern=r"^\d{10}$|^\d{13}$|^\d{17}$")


class BookingCreateIn(BaseModel):
    mode: Literal["bus"] = "bus"
    trip_id: str = Field(max_length=120)
    seats: list[str] = Field(min_length=1, max_length=4)
    boarding_point_id: str = Field(max_length=60)
    dropping_point_id: str = Field(max_length=60)
    passengers: list[PassengerIn] = Field(min_length=1, max_length=4)
    contact_phone: str = Field(min_length=10, max_length=20)
    contact_email: EmailStr | None = None
    expected_total: int | None = Field(
        default=None, description="Total (paisa) the app showed; a different live price returns price_changed"
    )

    @model_validator(mode="after")
    def _seats_match_passengers(self):
        if len(set(self.seats)) != len(self.seats):
            raise ValueError("duplicate seats")
        if sorted(p.seat_no for p in self.passengers) != sorted(self.seats):
            raise ValueError("each seat needs exactly one passenger")
        return self


class SegmentOut(BaseModel):
    trip_id: str
    carrier: str
    number: str
    from_id: str
    to_id: str
    depart_at: datetime
    arrive_at: datetime
    travel_class: str
    seat_numbers: list[str]
    boarding_point: dict | None = None
    dropping_point: dict | None = None


class PassengerOut(BaseModel):
    full_name: str
    ticket_number: str | None = None
    type: str
    gender: str
    age: int | None
    seat_no: str | None
    document_masked: str | None


class TicketInfo(BaseModel):
    qr_payload: str
    pdf_url: str
    issued_at: datetime


class BookingOut(BaseModel):
    id: uuid.UUID
    booking_ref: str
    mode: str
    status: str
    hold_expires_at: datetime | None
    base_fare: int
    service_charge: int
    discount: int
    total_amount: int
    currency: str
    contact_phone: str
    contact_email: str | None
    segments: list[SegmentOut]
    passengers: list[PassengerOut]
    created_at: datetime
    ticket: TicketInfo | None = None
