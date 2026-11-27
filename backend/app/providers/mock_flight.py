"""MOCK flight provider (stands in for Duffel / a GDS until real sandbox credentials exist).

Offers are rebuilt from their id, so search results, offer details and holds always agree. The
airlines and flight numbers are placeholders, not real carriers. Swap in a Duffel adapter that
implements the same `FlightProvider` interface later; nothing else has to change."""

import hashlib
import random
import secrets
from datetime import UTC, datetime, timedelta, timezone

from app.providers.base import (
    FareRules,
    FlightLeg,
    FlightOffer,
    FlightSlice,
    HoldResult,
    PaxCount,
    PaxFare,
    ProviderError,
    SliceQuery,
)

BD_TZ = timezone(timedelta(hours=6))
_TZ = {  # local time zones, so times on the ticket read like an airline's
    "CCU": 5.5, "DXB": 4, "KUL": 8, "SIN": 8, "BKK": 7, "JED": 3, "DOH": 3,
}  # fmt: skip

# Placeholder airlines (codes with a digit so they can't be mistaken for real ones).
_AIRLINES = [("S1", "Sample Air"), ("D2", "Demo Airways"), ("T3", "Test Wings"), ("M4", "Mock Air")]
_DOMESTIC = {"DAC", "CGP", "CXB", "ZYL", "JSR", "SPD", "RJH", "BZL"}

# Approximate block times in minutes.
_MINUTES = {
    frozenset({"DAC", "CXB"}): 65, frozenset({"DAC", "CGP"}): 50, frozenset({"DAC", "ZYL"}): 45,
    frozenset({"DAC", "JSR"}): 40, frozenset({"DAC", "SPD"}): 60, frozenset({"DAC", "RJH"}): 45,
    frozenset({"DAC", "BZL"}): 35, frozenset({"CGP", "CXB"}): 35, frozenset({"CGP", "ZYL"}): 50,
    frozenset({"DAC", "CCU"}): 60, frozenset({"DAC", "DXB"}): 330, frozenset({"DAC", "KUL"}): 240,
    frozenset({"DAC", "SIN"}): 260, frozenset({"DAC", "BKK"}): 150, frozenset({"DAC", "JED"}): 420,
    frozenset({"DAC", "DOH"}): 330, frozenset({"CGP", "DXB"}): 340, frozenset({"CGP", "KUL"}): 220,
    frozenset({"CGP", "JED"}): 430, frozenset({"ZYL", "DXB"}): 350,
}  # fmt: skip
_WEST = {"DXB", "DOH", "JED"}
_OPTIONS = 6
_CABIN_FACTOR = {"economy": 1.0, "premium_economy": 1.6, "business": 2.8}
_MAX_PAX = 9


def _rng(*parts: str) -> random.Random:
    seed = int(hashlib.sha256("|".join(parts).encode()).hexdigest()[:16], 16)
    return random.Random(seed)  # noqa: S311 - deterministic mock data, not security


def _code(location_id: str) -> str:
    return location_id.removeprefix("air_")


def _minutes(a: str, b: str) -> int:
    known = _MINUTES.get(frozenset({a, b}))
    if known:
        return known
    rng = _rng("minutes", *sorted((a, b)))
    if a in _DOMESTIC and b in _DOMESTIC:
        return 40 + rng.randint(0, 6) * 5
    return 150 + rng.randint(0, 50) * 5


def _has_direct(a: str, b: str) -> bool:
    if frozenset({a, b}) in _MINUTES:
        return True
    if a in _DOMESTIC and b in _DOMESTIC:
        return "DAC" in (a, b)  # domestic network is a hub-and-spoke around Dhaka
    return a not in _DOMESTIC and b not in _DOMESTIC  # abroad-to-abroad: assume a direct


def _local(code: str, utc: datetime) -> datetime:
    hours = _TZ.get(code, 6)
    return utc.astimezone(timezone(timedelta(hours=hours)))


class MockFlightProvider:
    name = "mock_flight"

    def __init__(self) -> None:
        self._holds: dict[str, str] = {}  # record locator -> held | ticketed | cancelled
        # Tests (and demos) can make the live price differ from what search showed.
        self.price_bump_paisa: int = 0
        # Tests (and demos) can delay a booked flight: record locator -> minutes.
        self.delays: dict[str, int] = {}

    # ---------------------------------------------------------------- ids

    @staticmethod
    def _offer_id(slices: list[SliceQuery], pax: PaxCount, cabin: str, option: int, brand: str) -> str:
        parts = "_".join(f"{_code(s.from_id)}.{_code(s.to_id)}.{s.on.strftime('%Y%m%d')}" for s in slices)
        return f"mf~{parts}~{pax.adults}.{pax.children}.{pax.infants}~{cabin}~{option}~{brand}"

    @staticmethod
    def _parse(offer_id: str) -> tuple[list[SliceQuery], PaxCount, str, int, str] | None:
        p = offer_id.split("~")
        if len(p) != 6 or p[0] != "mf" or p[3] not in _CABIN_FACTOR or p[5] not in ("saver", "flex", "business"):
            return None
        try:
            slices = []
            for chunk in p[1].split("_"):
                a, b, d = chunk.split(".")
                slices.append(SliceQuery(f"air_{a}", f"air_{b}", datetime.strptime(d, "%Y%m%d").date()))
            ad, ch, inf = (int(x) for x in p[2].split("."))
            return slices, PaxCount(ad, ch, inf), p[3], int(p[4]), p[5]
        except ValueError:
            return None

    # ---------------------------------------------------------------- schedule

    def _option(self, s: SliceQuery, index: int) -> FlightSlice:
        """The `index`-th flight of the day on this route (nonstop, or one stop via a hub)."""
        a, b = _code(s.from_id), _code(s.to_id)
        rng = _rng("opt", a, b, s.on.isoformat(), str(index))
        domestic = a in _DOMESTIC and b in _DOMESTIC
        hours = [7, 9, 11, 14, 17, 20] if domestic else [1, 8, 10, 14, 19, 23]
        dep = datetime(s.on.year, s.on.month, s.on.day, hours[index % len(hours)], rng.choice([0, 10, 25, 40, 55]), tzinfo=BD_TZ)
        code, name = _AIRLINES[(index + len(a + b)) % len(_AIRLINES)]

        def leg(x: str, y: str, at: datetime) -> FlightLeg:
            minutes = _minutes(x, y)
            return FlightLeg(
                carrier_code=code,
                carrier_name=name,
                flight_number=f"{code} {rng.randint(101, 989)}",
                from_id=f"air_{x}",
                to_id=f"air_{y}",
                depart_at=at,
                arrive_at=at + timedelta(minutes=minutes),
                aircraft="ATR 72-600" if minutes < 80 else ("Boeing 737-800" if minutes < 300 else "Boeing 787-9"),
            )

        direct = _has_direct(a, b)
        long_haul = _minutes(a, b) > 200
        one_stop = not direct or (long_haul and index >= 4)
        if not one_stop:
            return FlightSlice(s.from_id, s.to_id, [leg(a, b, dep)])
        if (a in _DOMESTIC and a != "DAC") or (b in _DOMESTIC and b != "DAC"):
            hub = "DAC"  # regional airports connect through Dhaka
        else:
            west = bool({a, b} & _WEST)
            hub = next(
                h for h in (["DOH", "DXB"] if west else ["BKK", "KUL"])[index % 2 :] + ["DOH", "DXB", "BKK"] if h not in (a, b)
            )
        first = leg(a, hub, dep)
        layover = timedelta(minutes=60 + rng.randint(0, 8) * 15)
        return FlightSlice(s.from_id, s.to_id, [first, leg(hub, b, first.arrive_at + layover)])

    # ---------------------------------------------------------------- pricing

    @staticmethod
    def _rules(brand: str, international: bool) -> FareRules:
        k = 100  # taka → paisa
        if brand == "saver":
            return FareRules(False, None, (6000 if international else 2500) * k, 24 if international else 3, 20, 7)
        if brand == "flex":
            return FareRules(True, (5000 if international else 1500) * k, (3000 if international else 1000) * k,
                             24 if international else 3, 30 if international else 20, 7)  # fmt: skip
        return FareRules(
            True, (2000 if international else 500) * k, 0, 12 if international else 2, 40 if international else 30, 10
        )

    def _fares(self, slices: list[FlightSlice], pax: PaxCount, cabin: str, brand: str, option: int, international: bool):
        taka = 0
        for sl in slices:
            minutes = sum(_minutes(_code(leg.from_id), _code(leg.to_id)) for leg in sl.legs)
            per = (2600 + minutes * 50) if not international else (9000 + minutes * 75)
            hour = sl.depart_at.astimezone(BD_TZ).hour
            per *= 1.12 if hour in (7, 8, 9, 17, 19, 20) else 1.0  # busy hours cost more
            per *= 0.92 if sl.stops else 1.0
            per *= 1 + _rng("p", sl.legs[0].flight_number, sl.depart_at.date().isoformat()).randint(0, 8) / 100
            taka += per
        taka *= _CABIN_FACTOR[cabin] * (1.18 if brand == "flex" else 1.0)
        base = int(round(taka / 10) * 10) * 100 + self.price_bump_paisa
        tax = (
            int(round(((2500 if international else 600) * len(slices) + base / 100 * (0.12 if international else 0.08)) / 10))
            * 1000
        )
        fares = [PaxFare("adult", pax.adults, base, tax)]
        if pax.children:
            fares.append(PaxFare("child", pax.children, int(base * 0.75) // 1000 * 1000, tax))
        if pax.infants:
            fares.append(PaxFare("infant", pax.infants, int(base * 0.10) // 1000 * 1000, 0))
        return fares

    def _build(self, slices: list[SliceQuery], pax: PaxCount, cabin: str, option: int, brand: str) -> FlightOffer | None:
        built = [self._option(s, (option + j * 2) % _OPTIONS) for j, s in enumerate(slices)]
        # A later journey must leave at least 2 hours after the previous one lands.
        if any(built[j].depart_at < built[j - 1].arrive_at + timedelta(hours=2) for j in range(1, len(built))):
            return None
        codes = {_code(leg.from_id) for sl in built for leg in sl.legs} | {_code(sl.to_id) for sl in built}
        international = not codes <= _DOMESTIC
        seats_left = _rng("seats", self._offer_id(slices, pax, cabin, option, brand)).randint(2, 9)
        if seats_left < pax.adults + pax.children:
            return None
        return FlightOffer(
            id=self._offer_id(slices, pax, cabin, option, brand),
            provider=self.name,
            slices=built,
            cabin=cabin,
            fare_brand=brand,
            rules=self._rules(brand, international),
            fares=self._fares(built, pax, cabin, brand, option, international),
            seats_left=seats_left,
            international=international,
        )

    # ---------------------------------------------------------------- interface

    async def search(self, slices: list[SliceQuery], pax: PaxCount, cabin: str) -> list[FlightOffer]:
        if not slices or any(
            s.from_id == s.to_id or not (s.from_id.startswith("air_") and s.to_id.startswith("air_")) for s in slices
        ):
            return []
        brands = ["business"] if cabin == "business" else ["saver", "flex"]
        cutoff = datetime.now(UTC) + timedelta(hours=2)  # can't sell a flight that leaves in < 2 h
        offers = []
        for option in range(_OPTIONS):
            for brand in brands:
                offer = self._build(slices, pax, cabin, option, brand)
                if offer is not None and offer.slices[0].depart_at > cutoff:
                    offers.append(offer)
        return offers

    async def get_details(self, offer_id: str) -> FlightOffer | None:
        parsed = self._parse(offer_id)
        return self._build(*parsed) if parsed else None

    async def hold(self, offer_id: str, passengers: list[dict], expires_at: datetime) -> HoldResult:
        if await self.get_details(offer_id) is None:
            raise ProviderError("offer_expired", "This fare is no longer available.", "এই ভাড়াটি আর পাওয়া যাচ্ছে না।")
        alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        locator = "".join(secrets.choice(alphabet) for _ in range(6))  # airline-style record locator
        self._holds[locator] = "held"
        return HoldResult(locator, expires_at)

    async def confirm(self, provider_ref: str) -> str:
        """Issues the tickets. Returns the PNR (unchanged record locator)."""
        if self._holds.get(provider_ref) not in ("held", "ticketed"):
            raise ProviderError("hold_not_found", "The airline could not find this booking.", "এয়ারলাইন এই বুকিংটি খুঁজে পায়নি।")
        self._holds[provider_ref] = "ticketed"
        return provider_ref

    @staticmethod
    def ticket_number(pnr: str, index: int) -> str:
        """13-digit e-ticket number. Prefix 000 = no real airline (mock)."""
        digits = int(hashlib.sha256(f"{pnr}:{index}".encode()).hexdigest()[:12], 16) % 10**10
        return f"000-{digits:010d}"

    async def cancel(self, provider_ref: str) -> None:
        self._holds[provider_ref] = "cancelled"

    async def get_status(self, provider_ref: str) -> str:
        return self._holds.get(provider_ref, "unknown")

    async def get_delay_minutes(self, provider_ref: str) -> int:
        """Schedule change for a ticketed booking (0 = on time). Real GDS: schedule-change messages."""
        return self.delays.get(provider_ref, 0)


def local_time(location_id: str, at: datetime) -> datetime:
    """Departure/arrival in the airport's own time zone."""
    return _local(_code(location_id), at)


_instance = MockFlightProvider()


def get_flight_provider() -> MockFlightProvider:
    return _instance


MAX_PASSENGERS = _MAX_PAX
