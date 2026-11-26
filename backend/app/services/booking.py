"""Bus search and the booking hold. Flow (see the spec, section 10):
search → seat map → create booking = provider hold + Redis seat locks → status `held` with
hold_expires_at → payment (Phase 4) → confirm/ticket. Unpaid holds expire and release seats."""

import secrets
import uuid
from datetime import date, timedelta

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import Settings
from app.core.crypto import mask_document
from app.core.envelope import AppError
from app.core.phone import normalize_bd_phone
from app.core.security import now
from app.models import Booking, BookingPassenger, BookingSegment, User
from app.providers.base import BusTrip, ProviderError
from app.providers.mock_bus import MockBusProvider
from app.schemas.booking import BookingCreateIn, BookingOut, PassengerOut, SegmentOut
from app.schemas.bus import BusTripOut, SeatMapOut, SeatOut, StopPointOut
from app.services.cache import KeyValueStore, seat_key

_REF_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"  # no 0/O/1/I to avoid misreading


class BookingErrors:
    @staticmethod
    def trip_not_found() -> AppError:
        return AppError(404, "trip_not_found", "This trip is no longer available.", "এই ট্রিপটি আর পাওয়া যাচ্ছে না।")

    @staticmethod
    def seat_unavailable() -> AppError:
        return AppError(
            409,
            "seat_unavailable",
            "Some seats were just taken. Please choose other seats.",
            "কিছু সিট এইমাত্র অন্য কেউ নিয়েছে। অন্য সিট বেছে নিন।",
        )

    @staticmethod
    def invalid_seats() -> AppError:
        return AppError(422, "invalid_seats", "Please choose valid seats.", "সঠিক সিট বেছে নিন।")

    @staticmethod
    def ladies_seat() -> AppError:
        return AppError(
            422,
            "ladies_seat",
            "Seats reserved for ladies can only be booked for female passengers.",
            "মহিলা সিট শুধু নারী যাত্রীর জন্য বুক করা যাবে।",
        )

    @staticmethod
    def invalid_point() -> AppError:
        return AppError(422, "invalid_point", "Please choose a boarding and a dropping point.", "ওঠার ও নামার স্থান বেছে নিন।")

    @staticmethod
    def price_changed(total_paisa: int) -> AppError:
        taka = f"{total_paisa // 100:,}"
        return AppError(
            409,
            "price_changed",
            f"The fare has changed. The new total is ৳ {taka}.",
            f"ভাড়া পরিবর্তন হয়েছে। নতুন মোট ভাড়া ৳ {taka}।",
        )

    @staticmethod
    def not_found() -> AppError:
        return AppError(404, "booking_not_found", "Booking not found.", "বুকিং পাওয়া যায়নি।")

    @staticmethod
    def not_cancellable() -> AppError:
        return AppError(
            409,
            "not_cancellable",
            "This booking can't be cancelled here yet.",
            "এই বুকিংটি এখান থেকে এখনো বাতিল করা যাবে না।",
        )

    @staticmethod
    def invalid_contact_phone() -> AppError:
        return AppError(422, "invalid_phone", "Enter a valid Bangladeshi mobile number.", "সঠিক বাংলাদেশি মোবাইল নম্বর দিন।")


def _trip_out(t: BusTrip) -> BusTripOut:
    def points(ps):
        return [StopPointOut(id=p.id, name_en=p.name_en, name_bn=p.name_bn, time=p.time) for p in ps]

    return BusTripOut(
        id=t.id,
        operator_name=t.operator_name,
        coach_number=t.coach_number,
        coach_type=t.coach_type,
        from_id=t.from_id,
        to_id=t.to_id,
        depart_at=t.depart_at,
        arrive_at=t.arrive_at,
        fare=t.fare_paisa,
        seats_available=t.seats_available,
        boarding_points=points(t.boarding_points),
        dropping_points=points(t.dropping_points),
    )


async def search_buses(
    provider: MockBusProvider, store: KeyValueStore, settings: Settings, from_id: str, to_id: str, on: date, coach: str | None
) -> list[BusTripOut]:
    key = f"jatri:bus:search:{provider.name}:{from_id}:{to_id}:{on.isoformat()}:{coach or 'any'}"
    cached = await store.get_json(key)
    if cached is not None:
        return [BusTripOut.model_validate(x) for x in cached]
    trips = [_trip_out(t) for t in await provider.search(from_id, to_id, on, coach)]
    await store.set_json(key, [t.model_dump(mode="json") for t in trips], settings.search_cache_seconds)
    return trips


async def seat_map(provider: MockBusProvider, store: KeyValueStore, settings: Settings, trip_id: str) -> SeatMapOut:
    trip = await provider.get_details(trip_id)
    layout = await provider.get_seats(trip_id)
    if trip is None or layout is None:
        raise BookingErrors.trip_not_found()
    numbers = [s.number for row in layout.rows for s in row if s]
    held = await store.owners([seat_key(trip_id, n) for n in numbers])
    held_numbers = {k.rsplit(":", 1)[1] for k in held}

    def out(seat):
        if seat is None:
            return None
        state = "held" if seat.state != "booked" and seat.number in held_numbers else seat.state
        return SeatOut(number=seat.number, state=state)

    return SeatMapOut(
        trip=_trip_out(trip),
        layout=layout.layout,
        rows=[[out(s) for s in row] for row in layout.rows],
        max_selectable=settings.max_bus_seats,
        service_charge_per_seat=settings.bus_service_charge_paisa,
    )


async def _new_ref(db: AsyncSession) -> str:
    while True:
        ref = "JT" + "".join(secrets.choice(_REF_ALPHABET) for _ in range(4))
        if await db.scalar(select(Booking.id).where(Booking.booking_ref == ref)) is None:
            return ref


async def create_bus_hold(
    db: AsyncSession,
    provider: MockBusProvider,
    store: KeyValueStore,
    settings: Settings,
    user: User,
    body: BookingCreateIn,
    idempotency_key: str | None,
) -> Booking:
    # Same request retried (bad network, double tap): return the booking we already made.
    if idempotency_key:
        existing = await db.scalar(select(Booking).where(Booking.user_id == user.id, Booking.idempotency_key == idempotency_key))
        if existing is not None:
            return await expire_if_needed(db, provider, store, existing)

    contact_phone = normalize_bd_phone(body.contact_phone)
    if contact_phone is None:
        raise BookingErrors.invalid_contact_phone()
    if len(body.seats) > settings.max_bus_seats:
        raise BookingErrors.invalid_seats()

    # Re-check live trip, price and seats with the provider (never trust what the app cached).
    trip = await provider.get_details(body.trip_id)
    layout = await provider.get_seats(body.trip_id)
    if trip is None or layout is None or trip.depart_at <= now():
        raise BookingErrors.trip_not_found()
    states = {s.number: s.state for row in layout.rows for s in row if s}
    if any(n not in states for n in body.seats):
        raise BookingErrors.invalid_seats()
    if any(states[n] == "booked" for n in body.seats):
        raise BookingErrors.seat_unavailable()
    for p in body.passengers:
        if states[p.seat_no] == "ladies" and p.gender != "female":
            raise BookingErrors.ladies_seat()
    boarding = next((p for p in trip.boarding_points if p.id == body.boarding_point_id), None)
    dropping = next((p for p in trip.dropping_points if p.id == body.dropping_point_id), None)
    if boarding is None or dropping is None:
        raise BookingErrors.invalid_point()

    count = len(body.seats)
    base = trip.fare_paisa * count
    service = settings.bus_service_charge_paisa * count
    total = base + service
    if body.expected_total is not None and body.expected_total != total:
        raise BookingErrors.price_changed(total)

    booking_id = uuid.uuid4()
    hold_seconds = settings.hold_minutes * 60
    keys = [seat_key(trip.id, n) for n in body.seats]
    if not await store.acquire_all(keys, str(booking_id), hold_seconds):
        raise BookingErrors.seat_unavailable()

    expires_at = now() + timedelta(seconds=hold_seconds)
    try:
        hold = await provider.hold(trip.id, body.seats, expires_at)
    except ProviderError as e:
        await store.release(keys, str(booking_id))
        raise AppError(409, e.code, e.message_en, e.message_bn) from e

    booking = Booking(
        id=booking_id,
        booking_ref=await _new_ref(db),
        user_id=user.id,
        mode="bus",
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
        segments=[
            BookingSegment(
                trip_id=trip.id,
                carrier=trip.operator_name,
                number=trip.coach_number,
                from_id=trip.from_id,
                to_id=trip.to_id,
                depart_at=trip.depart_at,
                arrive_at=trip.arrive_at,
                travel_class=trip.coach_type,
                seat_numbers=sorted(body.seats),
                extra={
                    "boarding_point": {
                        "id": boarding.id,
                        "name_en": boarding.name_en,
                        "name_bn": boarding.name_bn,
                        "time": boarding.time.isoformat(),
                    },
                    "dropping_point": {
                        "id": dropping.id,
                        "name_en": dropping.name_en,
                        "name_bn": dropping.name_bn,
                        "time": dropping.time.isoformat(),
                    },
                },
            )
        ],
        passengers=[
            BookingPassenger(
                position=i,
                full_name=" ".join(p.full_name.split()),
                type="adult" if (p.age is None or p.age >= 12) else "child",
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
        # A parallel request with the same idempotency key won the race.
        await db.rollback()
        await store.release(keys, str(booking_id))
        await provider.cancel(hold.provider_ref)
        existing = await db.scalar(select(Booking).where(Booking.user_id == user.id, Booking.idempotency_key == idempotency_key))
        if existing is None:
            raise
        return existing
    await db.refresh(booking)
    return booking


async def expire_if_needed(db: AsyncSession, provider: MockBusProvider, store: KeyValueStore, booking: Booking) -> Booking:
    """Unpaid hold past its deadline → cancelled, seats released. (A periodic job does the same
    for holds nobody looks at; it arrives with the background worker in Phase 4.)"""
    expires = booking.hold_expires_at
    if booking.status != "held" or expires is None:
        return booking
    if expires.tzinfo is None:
        expires = expires.replace(tzinfo=now().tzinfo)
    if expires > now():
        return booking
    await _release_hold(provider, store, booking)
    booking.status = "cancelled"
    await db.commit()
    await db.refresh(booking)
    return booking


async def _release_hold(provider: MockBusProvider, store: KeyValueStore, booking: Booking) -> None:
    for seg in booking.segments:
        await store.release([seat_key(seg.trip_id, n) for n in seg.seat_numbers], str(booking.id))
    if booking.provider_booking_ref:
        await provider.cancel(booking.provider_booking_ref)


async def get_owned(
    db: AsyncSession, provider: MockBusProvider, store: KeyValueStore, user: User, booking_id: uuid.UUID
) -> Booking:
    booking = await db.get(Booking, booking_id)
    # Same 404 whether it doesn't exist or belongs to someone else (no IDOR, no existence leak).
    if booking is None or booking.user_id != user.id:
        raise BookingErrors.not_found()
    return await expire_if_needed(db, provider, store, booking)


async def list_owned(db: AsyncSession, provider: MockBusProvider, store: KeyValueStore, user: User) -> list[Booking]:
    rows = (await db.scalars(select(Booking).where(Booking.user_id == user.id).order_by(Booking.created_at.desc()))).all()
    return [await expire_if_needed(db, provider, store, b) for b in rows]


async def cancel_hold(
    db: AsyncSession, provider: MockBusProvider, store: KeyValueStore, user: User, booking_id: uuid.UUID
) -> Booking:
    booking = await get_owned(db, provider, store, user, booking_id)
    if booking.status == "cancelled":
        return booking
    if booking.status != "held":
        raise BookingErrors.not_cancellable()  # paid bookings: cancellation + refund in Phase 7
    await _release_hold(provider, store, booking)
    booking.status = "cancelled"
    await db.commit()
    await db.refresh(booking)
    return booking


def booking_out(b: Booking) -> BookingOut:
    return BookingOut(
        id=b.id,
        booking_ref=b.booking_ref,
        mode=b.mode,
        status=b.status,
        hold_expires_at=b.hold_expires_at,
        base_fare=b.base_fare,
        service_charge=b.service_charge,
        discount=b.discount,
        total_amount=b.total_amount,
        currency=b.currency,
        contact_phone=b.contact_phone,
        contact_email=b.contact_email,
        created_at=b.created_at,
        segments=[
            SegmentOut(
                trip_id=s.trip_id,
                carrier=s.carrier,
                number=s.number,
                from_id=s.from_id,
                to_id=s.to_id,
                depart_at=s.depart_at,
                arrive_at=s.arrive_at,
                travel_class=s.travel_class,
                seat_numbers=list(s.seat_numbers),
                boarding_point=(s.extra or {}).get("boarding_point"),
                dropping_point=(s.extra or {}).get("dropping_point"),
            )
            for s in b.segments
        ],
        passengers=[
            PassengerOut(
                full_name=p.full_name,
                type=p.type,
                gender=p.gender,
                age=p.age,
                seat_no=p.seat_no,
                document_masked=mask_document(p.document_no),
            )
            for p in b.passengers
        ],
    )
