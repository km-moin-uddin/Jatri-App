"""MOCK train provider. Bangladesh Railway tickets can only be sold through the railway's own
system or an authorised partner, so this stands in until such an API is available. Trains are
placeholders ("Sample Express"), not the real timetable; fares follow the railway's per-km class
ratios approximately. Trips are rebuilt from their id, so search, coach maps and holds agree."""

import hashlib
import random
import secrets
from datetime import UTC, date, datetime, timedelta, timezone

from app.providers.base import (
    Coach,
    HoldResult,
    ProviderError,
    Seat,
    TrainClassFare,
    TrainTrip,
)

BD_TZ = timezone(timedelta(hours=6))

# Approximate rail km from Dhaka (Kamalapur); between two other stations a rough estimate is used.
_KM_FROM_DHAKA = {
    "stn_dhaka": 0, "stn_dhaka_airport": 15, "stn_joydebpur": 30, "stn_bhairab": 80, "stn_mymensingh": 120,
    "stn_akhaura": 130, "stn_cumilla": 160, "stn_sreemangal": 230, "stn_bogura": 250, "stn_chattogram": 320,
    "stn_sylhet": 320, "stn_jashore": 330, "stn_rajshahi": 345, "stn_benapole": 360, "stn_khulna": 400,
    "stn_rangpur": 400, "stn_dinajpur": 410, "stn_coxsbazar": 470, "stn_panchagarh": 520,
}  # fmt: skip

# Taka per km by class, roughly the railway's ratios (Shovan Chair Dhaka-Chattogram ≈ ৳345).
_RATE = {
    "shovan": 0.89, "shovan_chair": 1.08, "snigdha": 2.05, "ac_seat": 2.46,
    "f_seat": 1.64, "f_berth": 2.62, "ac_berth": 3.68,
}  # fmt: skip

# Coach names follow the railway's Bangla-letter style.
_COACHES = {
    "shovan": ["DA", "DHA"], "shovan_chair": ["GA", "GHA", "UMA"], "snigdha": ["CHA", "JA"],
    "ac_seat": ["SCHA"], "f_seat": ["NEO"], "f_berth": ["TA"], "ac_berth": ["KA", "KHA"],
}  # fmt: skip

_TRAINS = [
    ("Sample Express", ["shovan_chair", "snigdha", "ac_berth"]),
    ("Demo Intercity", ["shovan", "shovan_chair", "ac_seat"]),
    ("Test Mail", ["shovan", "shovan_chair", "f_seat"]),
    ("Mock Nishitha", ["shovan_chair", "snigdha", "f_berth", "ac_berth"]),
]
_DEPART_HOURS = [6, 9, 15, 22]
SALE_DAYS = 10  # Bangladesh Railway sells tickets up to 10 days ahead


def _rng(*parts: str) -> random.Random:
    seed = int(hashlib.sha256("|".join(parts).encode()).hexdigest()[:16], 16)
    return random.Random(seed)  # noqa: S311 - deterministic mock data, not security


def _km(a: str, b: str) -> int:
    ka, kb = _KM_FROM_DHAKA.get(a), _KM_FROM_DHAKA.get(b)
    if ka is None or kb is None:
        return 200
    if 0 in (ka, kb) or {a, b} & {"stn_dhaka_airport", "stn_joydebpur"}:
        return abs(ka - kb) or 15
    return max(60, int((ka + kb) * 0.55))  # two branch lines: back towards a junction and out again


class MockTrainProvider:
    name = "mock_train"

    def __init__(self) -> None:
        self._holds: dict[str, str] = {}  # ref -> held | confirmed | cancelled
        self._hold_seats: dict[str, tuple[str, list[str]]] = {}
        self._sold: dict[str, set[str]] = {}  # trip_id -> seats sold through us

    @staticmethod
    def _trip_id(from_id: str, to_id: str, on: date, index: int) -> str:
        return f"mt~{from_id}~{to_id}~{on.isoformat()}~{index}"

    @staticmethod
    def _parse(trip_id: str) -> tuple[str, str, date, int] | None:
        p = trip_id.split("~")
        if len(p) != 5 or p[0] != "mt":
            return None
        try:
            index = int(p[4])
            return (p[1], p[2], date.fromisoformat(p[3]), index) if 0 <= index < len(_TRAINS) else None
        except ValueError:
            return None

    def _coaches(self, from_id: str, to_id: str, on: date, index: int, travel_class: str) -> list[Coach]:
        rng = _rng("coach", from_id, to_id, on.isoformat(), str(index), travel_class)
        berth = travel_class.endswith("berth")
        booked_ratio = rng.uniform(0.3, 0.95)
        trip_id = self._trip_id(from_id, to_id, on, index)
        sold = self._sold.get(trip_id, set())
        coaches = []
        for name in _COACHES[travel_class][: 1 + rng.randint(0, 1)]:
            n = 1
            rows: list[list[Seat | None]] = []
            for _ in range(8 if berth else 15):
                row: list[Seat | None] = []
                for col in range(4):
                    if col == 2:
                        row.append(None)  # aisle (or the cabin wall for berths)
                    number = f"{name}-{n}"
                    state = "booked" if number in sold or rng.random() < booked_ratio else "available"
                    row.append(Seat(number, state))
                    n += 1
                rows.append(row)
            coaches.append(Coach(name, travel_class, "berth" if berth else "2+2", rows))
        return coaches

    def _build(self, from_id: str, to_id: str, on: date, index: int) -> TrainTrip:
        rng = _rng("train", from_id, to_id, on.isoformat(), str(index))
        km = _km(from_id, to_id)
        name, classes = _TRAINS[index]
        depart = datetime(on.year, on.month, on.day, _DEPART_HOURS[index], rng.choice([0, 15, 30, 45]), tzinfo=BD_TZ)
        speed = 48 if index != 2 else 36  # the mail train stops everywhere
        arrive = depart + timedelta(minutes=int(km / speed * 60) + rng.choice([0, 10, 20]))
        # Number: a stable 3-digit number per route and train (odd one way, even the other).
        base = int(hashlib.sha256(f"{min(from_id, to_id)}{max(from_id, to_id)}{index}".encode()).hexdigest()[:4], 16)
        number = 701 + (base % 90) * 2 + (0 if from_id < to_id else 1)
        off_day = [1, None, 4, None][index]  # Tuesday, daily, Friday, daily
        fares = []
        for c in classes:
            seats = sum(
                1
                for coach in self._coaches(from_id, to_id, on, index, c)
                for row in coach.rows
                for s in row
                if s and s.state == "available"
            )
            fares.append(TrainClassFare(c, max(50, round(km * _RATE[c] / 5) * 5) * 100, seats))
        return TrainTrip(
            id=self._trip_id(from_id, to_id, on, index),
            provider=self.name,
            train_name=name,
            train_number=str(number),
            from_id=from_id,
            to_id=to_id,
            depart_at=depart,
            arrive_at=arrive,
            off_day=off_day,
            classes=fares,
        )

    async def search(self, from_id: str, to_id: str, on: date, travel_class: str | None) -> list[TrainTrip]:
        if from_id == to_id or not (from_id.startswith("stn_") and to_id.startswith("stn_")):
            return []
        now = datetime.now(UTC)
        trips = []
        for i in range(len(_TRAINS)):
            trip = self._build(from_id, to_id, on, i)
            if trip.off_day == on.weekday() or trip.depart_at <= now:
                continue  # weekly off day, or already left
            if travel_class and all(c.travel_class != travel_class for c in trip.classes):
                continue
            trips.append(trip)
        return trips

    async def get_details(self, trip_id: str) -> TrainTrip | None:
        parsed = self._parse(trip_id)
        if parsed is None:
            return None
        trip = self._build(*parsed)
        return None if trip.off_day == parsed[2].weekday() else trip

    async def get_coaches(self, trip_id: str, travel_class: str) -> list[Coach] | None:
        trip = await self.get_details(trip_id)
        if trip is None or all(c.travel_class != travel_class for c in trip.classes):
            return None
        return self._coaches(*self._parse(trip_id), travel_class)

    async def hold(self, trip_id: str, travel_class: str, seats: list[str], expires_at: datetime) -> HoldResult:
        coaches = await self.get_coaches(trip_id, travel_class)
        if coaches is None:
            raise ProviderError("trip_not_found", "This train is no longer available.", "এই ট্রেনটি আর পাওয়া যাচ্ছে না।")
        states = {s.number: s.state for c in coaches for row in c.rows for s in row if s}
        if any(states.get(n) != "available" for n in seats):
            raise ProviderError(
                "seat_unavailable", "Some seats were just booked. Choose others.", "কিছু সিট এইমাত্র বুক হয়ে গেছে। অন্য সিট বেছে নিন।"
            )
        ref = "BR-" + secrets.token_hex(4).upper()
        self._holds[ref] = "held"
        self._hold_seats[ref] = (trip_id, list(seats))
        return HoldResult(ref, expires_at)

    async def confirm(self, provider_ref: str) -> str:
        if self._holds.get(provider_ref) not in ("held", "confirmed"):
            raise ProviderError("hold_not_found", "The railway could not find this hold.", "রেলওয়ে এই হোল্ডটি খুঁজে পায়নি।")
        trip_id, seats = self._hold_seats.get(provider_ref, ("", []))
        self._sold.setdefault(trip_id, set()).update(seats)
        self._holds[provider_ref] = "confirmed"
        return provider_ref.replace("BR-", "PNR-")

    async def cancel(self, provider_ref: str) -> None:
        self._holds[provider_ref] = "cancelled"

    async def get_status(self, provider_ref: str) -> str:
        return self._holds.get(provider_ref, "unknown")


_instance = MockTrainProvider()


def get_train_provider() -> MockTrainProvider:
    return _instance
