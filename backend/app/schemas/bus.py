from datetime import date, datetime
from typing import Literal

from pydantic import BaseModel, Field


class BusSearchIn(BaseModel):
    from_id: str = Field(max_length=40, examples=["bus_dhaka"])
    to_id: str = Field(max_length=40, examples=["bus_coxsbazar"])
    date: date
    coach_type: Literal["ac", "non_ac", "sleeper"] | None = None


class StopPointOut(BaseModel):
    id: str
    name_en: str
    name_bn: str
    time: datetime


class BusTripOut(BaseModel):
    id: str
    operator_name: str
    operator_logo_url: str | None = None
    coach_number: str
    coach_type: str
    from_id: str
    to_id: str
    depart_at: datetime
    arrive_at: datetime
    fare: int = Field(description="Per seat, in paisa")
    seats_available: int
    boarding_points: list[StopPointOut]
    dropping_points: list[StopPointOut]


class SeatOut(BaseModel):
    number: str
    state: Literal["available", "booked", "ladies", "held"]


class SeatMapOut(BaseModel):
    trip: BusTripOut
    layout: str
    rows: list[list[SeatOut | None]]
    max_selectable: int
    service_charge_per_seat: int
