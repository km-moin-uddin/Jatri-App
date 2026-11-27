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


# ---------------------------------------------------------------- flights


@dataclass(frozen=True)
class FlightLeg:
    """One flight (take-off to landing)."""

    carrier_code: str
    carrier_name: str
    flight_number: str
    from_id: str
    to_id: str
    depart_at: datetime
    arrive_at: datetime
    aircraft: str


@dataclass(frozen=True)
class FlightSlice:
    """One journey of the trip (outbound, return, or one city pair of a multi-city trip)."""

    from_id: str
    to_id: str
    legs: list[FlightLeg]

    @property
    def depart_at(self) -> datetime:
        return self.legs[0].depart_at

    @property
    def arrive_at(self) -> datetime:
        return self.legs[-1].arrive_at

    @property
    def duration_minutes(self) -> int:
        return int((self.arrive_at - self.depart_at).total_seconds() // 60)

    @property
    def stops(self) -> int:
        return len(self.legs) - 1


@dataclass(frozen=True)
class FareRules:
    """What the fare allows. Fees are per passenger in paisa; None = not allowed."""

    refundable: bool
    refund_fee: int | None
    change_fee: int | None
    # Refunds/changes are only possible until this many hours before the first departure.
    deadline_hours: int
    checked_baggage_kg: int
    cabin_baggage_kg: int


@dataclass(frozen=True)
class PaxFare:
    """Fare for one passenger type, per passenger, in paisa."""

    type: str  # adult | child | infant
    count: int
    base: int
    tax: int


@dataclass(frozen=True)
class FlightOffer:
    id: str
    provider: str
    slices: list[FlightSlice]
    cabin: str  # economy | premium_economy | business
    fare_brand: str  # saver | flex | business
    rules: FareRules
    fares: list[PaxFare]
    seats_left: int
    international: bool

    @property
    def base_total(self) -> int:
        return sum(f.base * f.count for f in self.fares)

    @property
    def tax_total(self) -> int:
        return sum(f.tax * f.count for f in self.fares)

    @property
    def total(self) -> int:
        return self.base_total + self.tax_total


@dataclass(frozen=True)
class SliceQuery:
    from_id: str
    to_id: str
    on: date


@dataclass(frozen=True)
class PaxCount:
    adults: int
    children: int
    infants: int


class FlightProvider(Protocol):
    name: str

    async def search(self, slices: list[SliceQuery], pax: PaxCount, cabin: str) -> list[FlightOffer]: ...
    async def get_details(self, offer_id: str) -> FlightOffer | None: ...
    async def hold(self, offer_id: str, passengers: list[dict], expires_at: datetime) -> HoldResult: ...
    async def confirm(self, provider_ref: str) -> str: ...
    async def cancel(self, provider_ref: str) -> None: ...
    async def get_status(self, provider_ref: str) -> str: ...


# ---------------------------------------------------------------- trains

TRAIN_CLASSES = ("shovan", "shovan_chair", "snigdha", "ac_seat", "f_seat", "f_berth", "ac_berth")


@dataclass(frozen=True)
class TrainClassFare:
    travel_class: str  # one of TRAIN_CLASSES
    fare_paisa: int  # per seat
    seats_available: int


@dataclass(frozen=True)
class TrainTrip:
    id: str
    provider: str
    train_name: str
    train_number: str
    from_id: str
    to_id: str
    depart_at: datetime
    arrive_at: datetime
    off_day: int | None  # weekday the train doesn't run (0 = Monday), None = runs daily
    classes: list[TrainClassFare] = field(default_factory=list)


@dataclass(frozen=True)
class Coach:
    """One coach of a class. Seat numbers carry the coach name, e.g. "CHA-25"."""

    name: str
    travel_class: str
    layout: str  # "2+2" chairs | "berth" cabins
    rows: list[list[Seat | None]]


class TrainProvider(Protocol):
    name: str

    async def search(self, from_id: str, to_id: str, on: date, travel_class: str | None) -> list[TrainTrip]: ...
    async def get_details(self, trip_id: str) -> TrainTrip | None: ...
    async def get_coaches(self, trip_id: str, travel_class: str) -> list[Coach] | None: ...
    async def hold(self, trip_id: str, travel_class: str, seats: list[str], expires_at: datetime) -> HoldResult: ...
    async def confirm(self, provider_ref: str) -> str: ...
    async def cancel(self, provider_ref: str) -> None: ...
    async def get_status(self, provider_ref: str) -> str: ...
