"""MOCK bus provider. Generates believable trips that are stable for a given route + date, so search
results, seat maps and holds agree with each other. Operator names are placeholders, not real
companies. Replace with an authorised aggregator / operator adapter later."""

import hashlib
import random
import secrets
from datetime import UTC, date, datetime, timedelta, timezone

from app.providers.base import BusTrip, HoldResult, ProviderError, Seat, SeatLayout, StopPoint

BD_TZ = timezone(timedelta(hours=6))

_OPERATORS = ["Sample Paribahan", "Demo Travels", "Test Express", "Night Coach Demo", "Mock Lines"]

# Approximate road hours between popular bus cities; unknown pairs fall back to a hash.
_HOURS = {
    frozenset({"bus_dhaka", "bus_coxsbazar"}): 10,
    frozenset({"bus_dhaka", "bus_chattogram"}): 6,
    frozenset({"bus_dhaka", "bus_sylhet"}): 6,
    frozenset({"bus_dhaka", "bus_rajshahi"}): 6,
    frozenset({"bus_dhaka", "bus_khulna"}): 7,
    frozenset({"bus_dhaka", "bus_barishal"}): 5,
    frozenset({"bus_dhaka", "bus_rangpur"}): 7,
    frozenset({"bus_chattogram", "bus_coxsbazar"}): 4,
}

# Boarding counters for big cities; others get "<City> Bus Terminal" / "<City> Counter".
_POINTS = {
    "bus_dhaka": [("Gabtoli", "গাবতলী"), ("Kalabagan", "কলাবাগান"), ("Sayedabad", "সায়েদাবাদ"), ("Arambagh", "আরামবাগ")],
    "bus_chattogram": [("Dampara", "দামপাড়া"), ("Oxygen Mor", "অক্সিজেন মোড়")],
    "bus_coxsbazar": [("Kolatoli", "কলাতলী"), ("Cox's Bazar Bus Terminal", "কক্সবাজার বাস টার্মিনাল")],
    "bus_sylhet": [("Kadamtoli", "কদমতলী"), ("Subhanighat", "সোবহানীঘাট")],
}


def _city_name(location_id: str) -> tuple[str, str]:
    from app.seed import load_locations

    for row in load_locations():
        if row["key"] == location_id:
            return row["city_en"], row["city_bn"]
    return location_id, location_id


def _rng(*parts: str) -> random.Random:
    seed = int(hashlib.sha256("|".join(parts).encode()).hexdigest()[:16], 16)
    return random.Random(seed)  # noqa: S311 - deterministic mock data, not security


def _points(location_id: str, base: datetime, offsets: list[int]) -> list[StopPoint]:
    names = _POINTS.get(location_id)
    if not names:
        en, bn = _city_name(location_id)
        names = [(f"{en} Bus Terminal", f"{bn} বাস টার্মিনাল"), (f"{en} Counter", f"{bn} কাউন্টার")]
    return [
        StopPoint(f"{location_id}:{i}", en, bn, base + timedelta(minutes=offsets[i % len(offsets)]))
        for i, (en, bn) in enumerate(names)
    ]


class MockBusProvider:
    name = "mock_bus"

    def __init__(self) -> None:
        self._holds: dict[str, str] = {}  # provider_ref -> status

    @staticmethod
    def _trip_id(from_id: str, to_id: str, on: date, index: int) -> str:
        return f"mb~{from_id}~{to_id}~{on.isoformat()}~{index}"

    @staticmethod
    def _parse(trip_id: str) -> tuple[str, str, date, int] | None:
        parts = trip_id.split("~")
        if len(parts) != 5 or parts[0] != "mb":
            return None
        try:
            return parts[1], parts[2], date.fromisoformat(parts[3]), int(parts[4])
        except ValueError:
            return None

    def _build(self, from_id: str, to_id: str, on: date, index: int) -> BusTrip:
        rng = _rng(from_id, to_id, on.isoformat(), str(index))
        hours = _HOURS.get(frozenset({from_id, to_id}), 4 + rng.randint(0, 6))
        depart_hour = [7, 9, 13, 21, 23][index % 5]
        depart = datetime(on.year, on.month, on.day, depart_hour, rng.choice([0, 15, 30, 45]), tzinfo=BD_TZ)
        arrive = depart + timedelta(hours=hours, minutes=rng.choice([0, 30]))
        coach_type = ["ac", "non_ac", "ac", "sleeper", "non_ac"][index % 5]
        per_hour = {"ac": 140, "non_ac": 90, "sleeper": 220}[coach_type]
        fare_taka = max(300, round(hours * per_hour / 50) * 50)
        layout = self._layout(from_id, to_id, on, index, coach_type)
        seats = [s for row in layout.rows for s in row if s]
        available = sum(1 for s in seats if s.state != "booked")
        return BusTrip(
            id=self._trip_id(from_id, to_id, on, index),
            provider=self.name,
            operator_name=_OPERATORS[index % len(_OPERATORS)],
            coach_number=f"{_OPERATORS[index % len(_OPERATORS)][:2].upper()}-{rng.randint(1, 99):02d}",
            coach_type=coach_type,
            from_id=from_id,
            to_id=to_id,
            depart_at=depart,
            arrive_at=arrive,
            fare_paisa=fare_taka * 100,
            seats_total=len(seats),
            seats_available=available,
            boarding_points=_points(from_id, depart, [-30, -15, 0, 10]),
            dropping_points=_points(to_id, arrive, [0, 15, 30]),
        )

    def _layout(self, from_id: str, to_id: str, on: date, index: int, coach_type: str) -> SeatLayout:
        rng = _rng("seats", from_id, to_id, on.isoformat(), str(index))
        sleeper = coach_type == "sleeper"
        letters = "ABCDEFGHIJ"[: 8 if sleeper else 10]
        per_side = (1, 2) if sleeper else (2, 2)
        rows: list[list[Seat | None]] = []
        booked_ratio = rng.uniform(0.25, 0.75)
        for r, letter in enumerate(letters):
            left = [f"{letter}{n}" for n in range(1, per_side[0] + 1)]
            right = [f"{letter}{n}" for n in range(per_side[0] + 1, per_side[0] + per_side[1] + 1)]

            def seat(num: str, row: int = r) -> Seat:
                if row == 3 and num.endswith(("1", "2")) and not sleeper:
                    return Seat(num, "ladies")
                return Seat(num, "booked" if rng.random() < booked_ratio else "available")

            rows.append([seat(n) for n in left] + [None] + [seat(n) for n in right])
        return SeatLayout(trip_id=self._trip_id(from_id, to_id, on, index), layout="2+1" if sleeper else "2+2", rows=rows)

    async def search(self, from_id: str, to_id: str, on: date, coach_type: str | None) -> list[BusTrip]:
        if from_id == to_id or not (from_id.startswith("bus_") and to_id.startswith("bus_")):
            return []
        trips = [self._build(from_id, to_id, on, i) for i in range(5)]
        now = datetime.now(UTC)
        trips = [t for t in trips if t.depart_at > now]  # nothing that already left
        if coach_type:
            trips = [t for t in trips if t.coach_type == coach_type]
        return trips

    async def get_details(self, trip_id: str) -> BusTrip | None:
        parsed = self._parse(trip_id)
        return self._build(*parsed) if parsed else None

    async def get_seats(self, trip_id: str) -> SeatLayout | None:
        parsed = self._parse(trip_id)
        if not parsed:
            return None
        trip = self._build(*parsed)
        return self._layout(*parsed, trip.coach_type)

    async def hold(self, trip_id: str, seats: list[str], expires_at: datetime) -> HoldResult:
        layout = await self.get_seats(trip_id)
        if layout is None:
            raise ProviderError("trip_not_found", "This trip is no longer available.", "এই ট্রিপটি আর পাওয়া যাচ্ছে না।")
        states = {s.number: s.state for row in layout.rows for s in row if s}
        if any(states.get(n) in (None, "booked") for n in seats):
            raise ProviderError(
                "seat_unavailable", "Some seats were just booked. Choose others.", "কিছু সিট এইমাত্র বুক হয়ে গেছে। অন্য সিট বেছে নিন।"
            )
        ref = "MOCK-" + secrets.token_hex(4).upper()
        self._holds[ref] = "held"
        return HoldResult(ref, expires_at)

    async def confirm(self, provider_ref: str) -> str:
        self._holds[provider_ref] = "confirmed"
        return provider_ref

    async def cancel(self, provider_ref: str) -> None:
        self._holds[provider_ref] = "cancelled"

    async def get_status(self, provider_ref: str) -> str:
        return self._holds.get(provider_ref, "unknown")


_instance = MockBusProvider()


def get_bus_provider() -> MockBusProvider:
    return _instance
