"""Flight search, offer details and the booking hold (spec section 10):
search → offer details (live re-price + fare rules) → create booking = provider hold (PNR) →
status `held` → payment → ticketing (shared with bus, see services/payment.py)."""

import hashlib
import json
import re
import uuid
from datetime import UTC, date, datetime, timedelta

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import Settings
from app.core.envelope import AppError
from app.core.phone import normalize_bd_phone
from app.core.security import now
from app.models import Booking, BookingPassenger, BookingSegment, User
from app.providers.base import FlightOffer, PaxCount, ProviderError, SliceQuery
from app.providers.mock_flight import local_time
from app.providers.registry import Providers
from app.schemas.flight import (
    FareRulesOut,
    FlightBookingIn,
    FlightLegOut,
    FlightOfferOut,
    FlightPassengerIn,
    FlightSearchIn,
    FlightSliceOut,
    PaxFareOut,
)
from app.services.booking import BookingErrors, find_retry, new_ref
from app.services.cache import KeyValueStore

_PASSPORT = r"^[A-Z0-9]{6,9}$"
_SELL_CUTOFF = timedelta(hours=2)


class FlightErrors:
    @staticmethod
    def offer_expired() -> AppError:
        return AppError(
            409,
            "offer_expired",
            "This fare is no longer available. Please search again.",
            "এই ভাড়াটি আর পাওয়া যাচ্ছে না। আবার খুঁজুন।",
        )

    @staticmethod
    def passengers_mismatch() -> AppError:
        return AppError(
            422,
            "passengers_mismatch",
            "The passengers don't match the number of adults, children and infants you searched for.",
            "যাত্রীর তথ্য খোঁজার সময়ের প্রাপ্তবয়স্ক, শিশু ও ইনফ্যান্টের সংখ্যার সাথে মিলছে না।",
        )

    @staticmethod
    def passenger_age(position: int) -> AppError:
        return AppError(
            422,
            "passenger_age",
            f"Passenger {position}: the date of birth doesn't match the passenger type (adult 12+, child 2-11, infant under 2).",
            f"যাত্রী {position}: জন্মতারিখ যাত্রীর ধরনের সাথে মিলছে না (প্রাপ্তবয়স্ক ১২+, শিশু ২-১১, ইনফ্যান্ট ২-এর কম)।",
        )

    @staticmethod
    def passport_required(position: int) -> AppError:
        return AppError(
            422,
            "passport_required",
            f"Passenger {position}: international flights need a passport number and its expiry date.",
            f"যাত্রী {position}: আন্তর্জাতিক ফ্লাইটে পাসপোর্ট নম্বর ও মেয়াদ শেষের তারিখ লাগবে।",
        )

    @staticmethod
    def passport_expiry(position: int) -> AppError:
        return AppError(
            422,
            "passport_expiry",
            f"Passenger {position}: the passport must be valid for at least 6 months after the trip.",
            f"যাত্রী {position}: যাত্রার পর অন্তত ৬ মাস পাসপোর্টের মেয়াদ থাকতে হবে।",
        )

    @staticmethod
    def duplicate_passenger() -> AppError:
        return AppError(422, "duplicate_passenger", "The same passenger is entered twice.", "একই যাত্রীর তথ্য দুবার দেওয়া হয়েছে।")


def _age(born: date, on: date) -> int:
    return on.year - born.year - ((on.month, on.day) < (born.month, born.day))


def _add_months(d: date, months: int) -> date:
    m = d.month - 1 + months
    y, m = d.year + m // 12, m % 12 + 1
    for day in (d.day, 30, 29, 28):
        try:
            return date(y, m, day)
        except ValueError:
            continue
    raise ValueError(d)


def paying_passengers(offer: FlightOffer) -> int:
    """Infants sit on a lap, so the service charge is per passenger with a seat."""
    return sum(f.count for f in offer.fares if f.type != "infant")


def offer_out(o: FlightOffer, settings: Settings) -> FlightOfferOut:
    def leg_out(leg) -> FlightLegOut:
        return FlightLegOut(
            carrier_code=leg.carrier_code,
            carrier_name=leg.carrier_name,
            flight_number=leg.flight_number,
            aircraft=leg.aircraft,
            from_id=leg.from_id,
            to_id=leg.to_id,
            from_code=leg.from_id.removeprefix("air_"),
            to_code=leg.to_id.removeprefix("air_"),
            depart_at=local_time(leg.from_id, leg.depart_at),
            arrive_at=local_time(leg.to_id, leg.arrive_at),
            duration_minutes=int((leg.arrive_at - leg.depart_at).total_seconds() // 60),
        )

    service = settings.flight_service_charge_paisa * paying_passengers(o)
    return FlightOfferOut(
        id=o.id,
        slices=[
            FlightSliceOut(
                from_id=s.from_id,
                to_id=s.to_id,
                depart_at=local_time(s.from_id, s.depart_at),
                arrive_at=local_time(s.to_id, s.arrive_at),
                duration_minutes=s.duration_minutes,
                stops=s.stops,
                legs=[leg_out(leg) for leg in s.legs],
            )
            for s in o.slices
        ],
        cabin=o.cabin,
        fare_brand=o.fare_brand,
        rules=rules_out(o),
        fares=[PaxFareOut(type=f.type, count=f.count, base=f.base, tax=f.tax) for f in o.fares],
        base_total=o.base_total,
        tax_total=o.tax_total,
        service_charge=service,
        total=o.total + service,
        seats_left=o.seats_left,
        international=o.international,
    )


def rules_out(o: FlightOffer) -> FareRulesOut:
    r = o.rules
    return FareRulesOut(
        refundable=r.refundable,
        refund_fee=r.refund_fee,
        change_fee=r.change_fee,
        deadline_hours=r.deadline_hours,
        checked_baggage_kg=r.checked_baggage_kg,
        cabin_baggage_kg=r.cabin_baggage_kg,
        no_show="refund_minus_fee" if r.refundable else "forfeit",
    )


async def search_flights(
    providers: Providers, store: KeyValueStore, settings: Settings, body: FlightSearchIn
) -> list[FlightOfferOut]:
    provider = providers.flight
    digest = hashlib.sha256(json.dumps(body.model_dump(mode="json"), sort_keys=True).encode()).hexdigest()[:24]
    key = f"jatri:flight:search:{provider.name}:{digest}"
    cached = await store.get_json(key)
    if cached is not None:
        return [FlightOfferOut.model_validate(x) for x in cached]
    offers = await provider.search(
        [SliceQuery(s.from_id, s.to_id, s.date) for s in body.slices],
        PaxCount(body.adults, body.children, body.infants),
        body.cabin,
    )
    out = [offer_out(o, settings) for o in offers]
    await store.set_json(key, [o.model_dump(mode="json") for o in out], settings.search_cache_seconds)
    return out


async def live_offer(providers: Providers, offer_id: str) -> FlightOffer:
    """Always asks the provider (never the search cache): the price shown before booking is live."""
    offer = await providers.flight.get_details(offer_id)
    if offer is None or offer.slices[0].depart_at <= now() + _SELL_CUTOFF:
        raise FlightErrors.offer_expired()
    return offer


def _check_passengers(offer: FlightOffer, passengers: list[FlightPassengerIn]) -> None:
    wanted = {f.type: f.count for f in offer.fares}
    got: dict[str, int] = {}
    for p in passengers:
        got[p.type] = got.get(p.type, 0) + 1
    if got != wanted:
        raise FlightErrors.passengers_mismatch()

    first_day = offer.slices[0].depart_at.date()
    last_day = offer.slices[-1].depart_at.date()
    today = now().date()
    seen = set()
    for i, p in enumerate(passengers, start=1):
        if p.date_of_birth > today:
            raise FlightErrors.passenger_age(i)
        age = _age(p.date_of_birth, last_day)
        if p.type == "adult" and _age(p.date_of_birth, first_day) < 12:
            raise FlightErrors.passenger_age(i)
        if p.type == "child" and not (2 <= _age(p.date_of_birth, first_day) and age < 12):
            raise FlightErrors.passenger_age(i)
        if p.type == "infant" and age >= 2:
            raise FlightErrors.passenger_age(i)
        if offer.international:
            if not p.document_no or not re.match(_PASSPORT, p.document_no) or p.document_expiry is None:
                raise FlightErrors.passport_required(i)
            if p.document_expiry < _add_months(last_day, 6):
                raise FlightErrors.passport_expiry(i)
        who = (" ".join(p.given_name.lower().split()), " ".join(p.surname.lower().split()), p.date_of_birth)
        if who in seen:
            raise FlightErrors.duplicate_passenger()
        seen.add(who)


def _utc(dt: datetime) -> datetime:
    return dt.astimezone(UTC)


async def create_flight_hold(
    db: AsyncSession,
    providers: Providers,
    store: KeyValueStore,
    settings: Settings,
    user: User,
    body: FlightBookingIn,
    idempotency_key: str | None,
) -> Booking:
    existing = await find_retry(db, providers, store, user, idempotency_key)
    if existing is not None:
        return existing
    contact_phone = normalize_bd_phone(body.contact_phone)
    if contact_phone is None:
        raise BookingErrors.invalid_contact_phone()

    provider = providers.flight
    offer = await live_offer(providers, body.offer_id)
    _check_passengers(offer, body.passengers)
    service = settings.flight_service_charge_paisa * paying_passengers(offer)
    total = offer.total + service
    if body.expected_total is not None and body.expected_total != total:
        raise BookingErrors.price_changed(total)

    expires_at = now() + timedelta(minutes=settings.hold_minutes)
    try:
        hold = await provider.hold(
            offer.id,
            [{"type": p.type, "given_name": p.given_name, "surname": p.surname} for p in body.passengers],
            expires_at,
        )
    except ProviderError as e:
        raise AppError(409, e.code, e.message_en, e.message_bn) from e

    baggage = f"{offer.rules.checked_baggage_kg} kg"
    booking = Booking(
        id=uuid.uuid4(),
        booking_ref=await new_ref(db),
        user_id=user.id,
        mode="flight",
        provider=provider.name,
        provider_booking_ref=hold.provider_ref,
        status="held",
        hold_expires_at=hold.expires_at,
        base_fare=offer.base_total,
        service_charge=service,
        discount=0,
        total_amount=total,
        contact_phone=contact_phone,
        contact_email=body.contact_email,
        idempotency_key=idempotency_key,
        details={
            "offer_id": offer.id,
            "cabin": offer.cabin,
            "fare_brand": offer.fare_brand,
            "international": offer.international,
            "taxes": offer.tax_total,
            "rules": rules_out(offer).model_dump(),
        },
        segments=[
            BookingSegment(
                trip_id=offer.id,
                carrier=leg.carrier_name,
                number=leg.flight_number,
                from_id=leg.from_id,
                to_id=leg.to_id,
                depart_at=_utc(leg.depart_at),  # stored in UTC; shown in the airport's local time
                arrive_at=_utc(leg.arrive_at),
                travel_class=offer.cabin,
                seat_numbers=[],
                baggage=baggage,
                extra={
                    "slice_index": j,
                    "from_code": leg.from_id.removeprefix("air_"),
                    "to_code": leg.to_id.removeprefix("air_"),
                    "aircraft": leg.aircraft,
                    "carrier_code": leg.carrier_code,
                },
            )
            for j, s in enumerate(offer.slices)
            for leg in s.legs
        ],
        passengers=[
            BookingPassenger(
                position=i,
                full_name=" ".join(f"{p.given_name} {p.surname}".upper().split()),
                type=p.type,
                gender=p.gender,
                age=_age(p.date_of_birth, offer.slices[0].depart_at.date()),
                date_of_birth=p.date_of_birth,
                nationality=p.nationality,
                document_no=p.document_no,
                document_expiry=p.document_expiry,
                seat_no=None,
            )
            for i, p in enumerate(body.passengers)
        ],
    )
    db.add(booking)
    try:
        await db.commit()
    except IntegrityError:
        await db.rollback()  # a parallel retry with the same idempotency key won
        await provider.cancel(hold.provider_ref)
        existing = await db.scalar(select(Booking).where(Booking.user_id == user.id, Booking.idempotency_key == idempotency_key))
        if existing is None:
            raise
        return existing
    await db.refresh(booking)
    return booking
