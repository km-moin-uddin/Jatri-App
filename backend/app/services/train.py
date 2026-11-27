"""Train search, coach/seat maps and the seat hold. Same flow as buses (search → seats → hold →
payment → ticket) with the railway's rules: sales open 10 days ahead, trains have a weekly off day,
seats belong to a class and a coach, and every passenger aged 18+ needs an NID."""

import uuid
from datetime import date, timedelta

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import Settings
from app.core.envelope import AppError
from app.core.phone import normalize_bd_phone
from app.core.security import now
from app.models import Booking, BookingPassenger, BookingSegment, User
from app.providers.base import ProviderError, TrainTrip
from app.providers.mock_train import BD_TZ, SALE_DAYS
from app.providers.registry import Providers
from app.schemas.bus import SeatOut
from app.schemas.train import CoachOut, TrainBookingIn, TrainClassOut, TrainSeatMapOut, TrainTripOut
from app.services.booking import BookingErrors, find_retry, new_ref
from app.services.cache import KeyValueStore, seat_key

_DAYS = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"]
ADULT_AGE = 18


class TrainErrors:
    @staticmethod
    def outside_sale_window() -> AppError:
        return AppError(
            422,
            "outside_sale_window",
            f"Train tickets go on sale {SALE_DAYS} days before the journey. Choose a date within the next {SALE_DAYS} days.",
            f"যাত্রার {SALE_DAYS} দিন আগে থেকে ট্রেনের টিকিট বিক্রি হয়। আগামী {SALE_DAYS} দিনের মধ্যে একটি তারিখ বেছে নিন।",
        )

    @staticmethod
    def nid_required(position: int) -> AppError:
        return AppError(
            422,
            "nid_required",
            f"Passenger {position}: Bangladesh Railway needs the NID number of every passenger aged 18 or over.",
            f"যাত্রী {position}: ১৮ বছর বা তার বেশি বয়সী প্রত্যেক যাত্রীর এনআইডি নম্বর রেলওয়ের জন্য প্রয়োজন।",
        )

    @staticmethod
    def class_not_found() -> AppError:
        return AppError(404, "class_not_found", "This class is not available on this train.", "এই ট্রেনে এই শ্রেণির আসন নেই।")


def _today_bd() -> date:
    return now().astimezone(BD_TZ).date()


def check_sale_window(on: date) -> None:
    today = _today_bd()
    if on < today or on > today + timedelta(days=SALE_DAYS):
        raise TrainErrors.outside_sale_window()


def trip_out(t: TrainTrip) -> TrainTripOut:
    return TrainTripOut(
        id=t.id,
        train_name=t.train_name,
        train_number=t.train_number,
        from_id=t.from_id,
        to_id=t.to_id,
        depart_at=t.depart_at,
        arrive_at=t.arrive_at,
        off_day=_DAYS[t.off_day] if t.off_day is not None else None,
        classes=[
            TrainClassOut(travel_class=c.travel_class, fare=c.fare_paisa, seats_available=c.seats_available) for c in t.classes
        ],
    )


async def search_trains(
    providers: Providers, store: KeyValueStore, settings: Settings, from_id: str, to_id: str, on: date, travel_class: str | None
) -> list[TrainTripOut]:
    check_sale_window(on)
    provider = providers.train
    key = f"jatri:train:search:{provider.name}:{from_id}:{to_id}:{on.isoformat()}:{travel_class or 'any'}"
    cached = await store.get_json(key)
    if cached is not None:
        return [TrainTripOut.model_validate(x) for x in cached]
    trips = [trip_out(t) for t in await provider.search(from_id, to_id, on, travel_class)]
    await store.set_json(key, [t.model_dump(mode="json") for t in trips], settings.search_cache_seconds)
    return trips


async def seat_map(
    providers: Providers, store: KeyValueStore, settings: Settings, trip_id: str, travel_class: str
) -> TrainSeatMapOut:
    provider = providers.train
    trip = await provider.get_details(trip_id)
    if trip is None:
        raise BookingErrors.trip_not_found()
    fare = next((c for c in trip.classes if c.travel_class == travel_class), None)
    coaches = await provider.get_coaches(trip_id, travel_class)
    if fare is None or coaches is None:
        raise TrainErrors.class_not_found()
    numbers = [s.number for c in coaches for row in c.rows for s in row if s]
    held = {k.rsplit(":", 1)[1] for k in await store.owners([seat_key(trip_id, n) for n in numbers])}

    def out(seat):
        if seat is None:
            return None
        return SeatOut(number=seat.number, state="held" if seat.state == "available" and seat.number in held else seat.state)

    return TrainSeatMapOut(
        trip=trip_out(trip),
        travel_class=travel_class,
        fare=fare.fare_paisa,
        coaches=[CoachOut(name=c.name, layout=c.layout, rows=[[out(s) for s in row] for row in c.rows]) for c in coaches],
        max_selectable=settings.max_train_seats,
        service_charge_per_seat=settings.train_service_charge_paisa,
    )


async def create_train_hold(
    db: AsyncSession,
    providers: Providers,
    store: KeyValueStore,
    settings: Settings,
    user: User,
    body: TrainBookingIn,
    idempotency_key: str | None,
) -> Booking:
    existing = await find_retry(db, providers, store, user, idempotency_key)
    if existing is not None:
        return existing
    contact_phone = normalize_bd_phone(body.contact_phone)
    if contact_phone is None:
        raise BookingErrors.invalid_contact_phone()
    if len(body.seats) > settings.max_train_seats:
        raise BookingErrors.invalid_seats()
    for i, p in enumerate(body.passengers, start=1):
        if p.age >= ADULT_AGE and not p.document_no:
            raise TrainErrors.nid_required(i)

    provider = providers.train
    trip = await provider.get_details(body.trip_id)
    if trip is None or trip.depart_at <= now():
        raise BookingErrors.trip_not_found()
    check_sale_window(trip.depart_at.astimezone(BD_TZ).date())
    fare = next((c for c in trip.classes if c.travel_class == body.travel_class), None)
    coaches = await provider.get_coaches(trip.id, body.travel_class)
    if fare is None or coaches is None:
        raise TrainErrors.class_not_found()
    coach_of = {s.number: c.name for c in coaches for row in c.rows for s in row if s}
    states = {s.number: s.state for c in coaches for row in c.rows for s in row if s}
    if any(n not in states for n in body.seats):
        raise BookingErrors.invalid_seats()
    if any(states[n] != "available" for n in body.seats):
        raise BookingErrors.seat_unavailable()

    count = len(body.seats)
    base = fare.fare_paisa * count
    service = settings.train_service_charge_paisa * count
    total = base + service
    if body.expected_total is not None and body.expected_total != total:
        raise BookingErrors.price_changed(total)

    booking_id = uuid.uuid4()
    hold_seconds = settings.hold_minutes * 60
    keys = [seat_key(trip.id, n) for n in body.seats]
    if not await store.acquire_all(keys, str(booking_id), hold_seconds):
        raise BookingErrors.seat_unavailable()
    try:
        hold = await provider.hold(trip.id, body.travel_class, body.seats, now() + timedelta(seconds=hold_seconds))
    except ProviderError as e:
        await store.release(keys, str(booking_id))
        raise AppError(409, e.code, e.message_en, e.message_bn) from e

    booking = Booking(
        id=booking_id,
        booking_ref=await new_ref(db),
        user_id=user.id,
        mode="train",
        provider=provider.name,
        provider_booking_ref=hold.provider_ref,
        status="held",
        hold_expires_at=hold.expires_at,
        base_fare=base,
        service_charge=service,
        discount=0,
        total_amount=total,
        contact_phone=contact_phone,
        contact_email=body.contact_email,
        idempotency_key=idempotency_key,
        details={"travel_class": body.travel_class},
        segments=[
            BookingSegment(
                trip_id=trip.id,
                carrier=trip.train_name,
                number=trip.train_number,
                from_id=trip.from_id,
                to_id=trip.to_id,
                depart_at=trip.depart_at,
                arrive_at=trip.arrive_at,
                travel_class=body.travel_class,
                seat_numbers=sorted(body.seats, key=lambda n: (n.split("-")[0], int(n.split("-")[1]))),
                extra={"coach": ", ".join(sorted({coach_of[n] for n in body.seats}))},
            )
        ],
        passengers=[
            BookingPassenger(
                position=i,
                full_name=" ".join(p.full_name.split()),
                type="adult" if p.age >= 12 else "child",
                gender=p.gender,
                age=p.age,
                document_no=p.document_no,
                seat_no=p.seat_no,
            )
            for i, p in enumerate(body.passengers)
        ],
    )
    db.add(booking)
    try:
        await db.commit()
    except IntegrityError:
        await db.rollback()
        await store.release(keys, str(booking_id))
        await provider.cancel(hold.provider_ref)
        existing = await db.scalar(select(Booking).where(Booking.user_id == user.id, Booking.idempotency_key == idempotency_key))
        if existing is None:
            raise
        return existing
    await db.refresh(booking)
    return booking
