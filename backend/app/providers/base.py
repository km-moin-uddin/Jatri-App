"""Provider adapter pattern. Each transport type has one interface; any number of providers
(mock, operator APIs, aggregators) implement it and return OUR common models."""

from dataclasses import dataclass, field
from datetime import date, datetime
from typing import Protocol


@dataclass(frozen=True)
class StopPoint:
    id: str
    name_en: str
    name_bn: str
    time: datetime


@dataclass(frozen=True)
class BusTrip:
    id: str
    provider: str
    operator_name: str
    coach_number: str
    coach_type: str  # ac | non_ac | sleeper
    from_id: str
    to_id: str
    depart_at: datetime
    arrive_at: datetime
    fare_paisa: int
    seats_total: int
    seats_available: int
    boarding_points: list[StopPoint] = field(default_factory=list)
    dropping_points: list[StopPoint] = field(default_factory=list)


@dataclass(frozen=True)
class Seat:
    number: str
    state: str  # available | booked | ladies


@dataclass(frozen=True)
class SeatLayout:
    trip_id: str
    layout: str  # "2+2" | "2+1"
    rows: list[list[Seat | None]]  # None = aisle


@dataclass(frozen=True)
class HoldResult:
    provider_ref: str
    expires_at: datetime


class ProviderError(Exception):
    """The provider refused (seat gone, trip cancelled...). Message is safe to show."""

    def __init__(self, code: str, message_en: str, message_bn: str):
        self.code = code
        self.message_en = message_en
        self.message_bn = message_bn


class BusProvider(Protocol):
    name: str

    async def search(self, from_id: str, to_id: str, on: date, coach_type: str | None) -> list[BusTrip]: ...
    async def get_details(self, trip_id: str) -> BusTrip | None: ...
    async def get_seats(self, trip_id: str) -> SeatLayout | None: ...
    async def hold(self, trip_id: str, seats: list[str], expires_at: datetime) -> HoldResult: ...
    async def confirm(self, provider_ref: str) -> str: ...
    async def cancel(self, provider_ref: str) -> None: ...
    async def get_status(self, provider_ref: str) -> str: ...
