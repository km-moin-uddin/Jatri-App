import uuid
from typing import Annotated

from fastapi import APIRouter, Header

from app.api.deps import AppSettings, CurrentUser, DbSession
from app.api.v1.buses import StoreDep
from app.api.v1.payments import Gateway
from app.core.envelope import ok
from app.core.text import bn_digits
from app.providers.registry import ProvidersDep
from app.schemas.booking import BookingCreateIn, BookingOut, BusBookingIn, CancelIn, CancelQuoteOut
from app.schemas.common import Envelope
from app.schemas.train import TrainBookingIn
from app.services import booking as service
from app.services import cancellation
from app.services import flight as flight_service
from app.services import train as train_service

router = APIRouter(prefix="/bookings", tags=["bookings"])


@router.post("", response_model=Envelope[BookingOut])
async def create(
    body: BookingCreateIn,
    user: CurrentUser,
    db: DbSession,
    providers: ProvidersDep,
    store: StoreDep,
    settings: AppSettings,
    idempotency_key: Annotated[str | None, Header(max_length=64)] = None,
):
    """Holds the seats (bus, train) or the fare (flight, with a PNR) for `hold_minutes` (status `held`).
    `mode` picks the body shape. Send an `Idempotency-Key` header so a retried request returns the
    same booking instead of holding twice."""
    if isinstance(body, BusBookingIn):
        booking = await service.create_bus_hold(db, providers, store, settings, user, body, idempotency_key)
    elif isinstance(body, TrainBookingIn):
        booking = await train_service.create_train_hold(db, providers, store, settings, user, body, idempotency_key)
    else:
        booking = await flight_service.create_flight_hold(db, providers, store, settings, user, body, idempotency_key)
    return ok(await service.full_out(db, booking))


@router.get("", response_model=Envelope[list[BookingOut]])
async def list_bookings(user: CurrentUser, db: DbSession, providers: ProvidersDep, store: StoreDep):
    """Newest first, all modes (My Trips splits them into upcoming, completed and cancelled)."""
    return ok([await service.full_out(db, b) for b in await service.list_owned(db, providers, store, user)])


@router.get("/{booking_id}", response_model=Envelope[BookingOut])
async def get(booking_id: uuid.UUID, user: CurrentUser, db: DbSession, providers: ProvidersDep, store: StoreDep):
    return ok(await service.full_out(db, await service.get_owned(db, providers, store, user, booking_id)))


@router.get("/{booking_id}/cancel-quote", response_model=Envelope[CancelQuoteOut])
async def cancel_quote(booking_id: uuid.UUID, user: CurrentUser, db: DbSession, providers: ProvidersDep, store: StoreDep):
    """What cancelling now would give back (refund), keep (fee) and why, before the user confirms."""
    q = cancellation.quote(await service.get_owned(db, providers, store, user, booking_id))
    return ok(
        CancelQuoteOut(
            cancellable=q.cancellable,
            paid=q.paid,
            refund=q.refund,
            fee=q.fee,
            non_refundable=q.non_refundable,
            reason=q.reason,
            message_en=q.message_en,
            message_bn=bn_digits(q.message_bn),
            deadline=q.deadline,
        )
    )


@router.post("/{booking_id}/cancel", response_model=Envelope[BookingOut])
async def cancel(
    booking_id: uuid.UUID,
    user: CurrentUser,
    db: DbSession,
    providers: ProvidersDep,
    store: StoreDep,
    settings: AppSettings,
    gateway: Gateway,
    body: CancelIn | None = None,
):
    """An unpaid hold is released. A ticket is cancelled with the provider by its quote (send
    `expected_refund` from the quote; `refund_changed` if it moved), and the refund is started."""
    booking = await cancellation.cancel(
        db, providers, store, settings, gateway, user, booking_id, body.expected_refund if body else None
    )
    return ok(await service.full_out(db, booking))
