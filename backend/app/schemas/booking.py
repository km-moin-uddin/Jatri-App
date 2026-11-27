import uuid
from datetime import date, datetime
from typing import Annotated, Literal

from pydantic import BaseModel, Discriminator, EmailStr, Field, Tag, model_validator

from app.schemas.flight import FareRulesOut, FlightBookingIn
from app.schemas.train import TrainBookingIn


class PassengerIn(BaseModel):
    full_name: str = Field(min_length=2, max_length=80)
    gender: Literal["male", "female", "other"]
    age: int | None = Field(default=None, ge=1, le=120)
    seat_no: str = Field(max_length=10)
    # NID: 10, 13 or 17 digits. Optional for bus, required for train (Phase 6). Encrypted at rest.
    document_no: str | None = Field(default=None, pattern=r"^\d{10}$|^\d{13}$|^\d{17}$")


class BusBookingIn(BaseModel):
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


def _mode(v) -> str:
    return (v.get("mode") if isinstance(v, dict) else getattr(v, "mode", None)) or "bus"


# `mode` picks the shape: "bus" (the default, so Phase 3 apps keep working) or "flight".
BookingCreateIn = Annotated[
    Annotated[BusBookingIn, Tag("bus")] | Annotated[FlightBookingIn, Tag("flight")] | Annotated[TrainBookingIn, Tag("train")],
    Discriminator(_mode),
]


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
    # Trains: the coach of the seats.
    coach: str | None = None
    # Flights: which journey this flight belongs to (0 = outbound), airport codes and baggage.
    slice_index: int | None = None
    from_code: str | None = None
    to_code: str | None = None
    aircraft: str | None = None
    baggage: str | None = None


class PassengerOut(BaseModel):
    full_name: str
    ticket_number: str | None = None
    type: str
    gender: str
    age: int | None
    seat_no: str | None
    document_masked: str | None
    date_of_birth: date | None = None
    nationality: str | None = None


class TicketInfo(BaseModel):
    qr_payload: str
    pdf_url: str
    issued_at: datetime


class RefundInfo(BaseModel):
    amount: int
    fee: int
    status: Literal["pending", "processing", "refunded", "failed"]
    processed_at: datetime | None


class CancelQuoteOut(BaseModel):
    cancellable: bool
    paid: int
    refund: int = Field(description="What goes back to the customer, paisa")
    fee: int = Field(description="Cancellation fee kept, paisa")
    non_refundable: int = Field(description="Service charge, never refunded")
    reason: str | None = Field(description="too_late | already_departed | non_refundable | not_cancellable")
    message_en: str
    message_bn: str
    deadline: datetime | None = Field(description="Last moment the booking can be cancelled")


class CancelIn(BaseModel):
    expected_refund: int | None = Field(
        default=None, description="The quote's refund; a different live amount returns refund_changed"
    )


class BookingOut(BaseModel):
    id: uuid.UUID
    booking_ref: str
    mode: str
    status: str
    hold_expires_at: datetime | None
    base_fare: int
    taxes: int = Field(default=0, description="Flights: airport/government taxes, paisa")
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
    # Flights: cabin, fare brand and the fare rules that apply to this booking.
    cabin: str | None = None
    fare_brand: str | None = None
    fare_rules: FareRulesOut | None = None
    promo_code: str | None = Field(default=None, description="Applied promo code; `discount` holds its amount")
    cancelled_at: datetime | None = None
    refund: RefundInfo | None = None
