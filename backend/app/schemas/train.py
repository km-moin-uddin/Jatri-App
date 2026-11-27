from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, EmailStr, Field, model_validator

from app.schemas.bus import SeatOut

TrainClass = Literal["shovan", "shovan_chair", "snigdha", "ac_seat", "f_seat", "f_berth", "ac_berth"]


class TrainSearchIn(BaseModel):
    from_id: str = Field(max_length=40, examples=["stn_dhaka"])
    to_id: str = Field(max_length=40, examples=["stn_chattogram"])
    date: date
    travel_class: TrainClass | None = Field(default=None, description="Only trains that have this class")


class TrainClassOut(BaseModel):
    travel_class: TrainClass
    fare: int = Field(description="Per seat, paisa")
    seats_available: int


class TrainTripOut(BaseModel):
    id: str
    train_name: str
    train_number: str
    from_id: str
    to_id: str
    depart_at: datetime
    arrive_at: datetime
    off_day: Literal["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"] | None = Field(
        description="Weekly day the train doesn't run; null = daily"
    )
    classes: list[TrainClassOut]


class CoachOut(BaseModel):
    name: str
    layout: Literal["2+2", "berth"]
    rows: list[list[SeatOut | None]]


class TrainSeatMapOut(BaseModel):
    trip: TrainTripOut
    travel_class: TrainClass
    fare: int
    coaches: list[CoachOut]
    max_selectable: int
    service_charge_per_seat: int


class TrainPassengerIn(BaseModel):
    full_name: str = Field(min_length=2, max_length=80)
    gender: Literal["male", "female", "other"]
    age: int = Field(ge=1, le=120)
    seat_no: str = Field(max_length=12)
    # Bangladesh Railway needs the NID of every passenger aged 18+; under 18 may give a
    # 17-digit birth registration number. Encrypted at rest.
    document_no: str | None = Field(default=None, pattern=r"^\d{10}$|^\d{13}$|^\d{17}$")


class TrainBookingIn(BaseModel):
    mode: Literal["train"]
    trip_id: str = Field(max_length=120)
    travel_class: TrainClass
    seats: list[str] = Field(min_length=1, max_length=4)
    passengers: list[TrainPassengerIn] = Field(min_length=1, max_length=4)
    contact_phone: str = Field(min_length=10, max_length=20)
    contact_email: EmailStr | None = None
    expected_total: int | None = None

    @model_validator(mode="after")
    def _seats_match_passengers(self):
        if len(set(self.seats)) != len(self.seats):
            raise ValueError("duplicate seats")
        if sorted(p.seat_no for p in self.passengers) != sorted(self.seats):
            raise ValueError("each seat needs exactly one passenger")
        return self
