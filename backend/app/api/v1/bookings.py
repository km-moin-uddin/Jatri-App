import uuid
from typing import Annotated

from fastapi import APIRouter, Header

from app.api.deps import AppSettings, CurrentUser, DbSession
from app.api.v1.buses import StoreDep
from app.core.envelope import ok
from app.providers.registry import ProvidersDep
from app.schemas.booking import BookingCreateIn, BookingOut, BusBookingIn
from app.schemas.common import Envelope
from app.services import booking as service
from app.services import flight as flight_service

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
    """Holds the seats (bus) or the fare (flight, with a PNR) for `hold_minutes` (status `held`).
    `mode` picks the body shape. Send an `Idempotency-Key` header so a retried request returns the
    same booking instead of holding twice."""
    if isinstance(body, BusBookingIn):
        booking = await service.create_bus_hold(db, providers, store, settings, user, body, idempotency_key)
    else:
        booking = await flight_service.create_flight_hold(db, providers, store, settings, user, body, idempotency_key)
    return ok(service.booking_out(booking, await service.ticket_of(db, booking)))


@router.get("", response_model=Envelope[list[BookingOut]])
async def list_bookings(user: CurrentUser, db: DbSession, providers: ProvidersDep, store: StoreDep):
    return ok(
        [service.booking_out(b, await service.ticket_of(db, b)) for b in await service.list_owned(db, providers, store, user)]
    )


@router.get("/{booking_id}", response_model=Envelope[BookingOut])
async def get(booking_id: uuid.UUID, user: CurrentUser, db: DbSession, providers: ProvidersDep, store: StoreDep):
    booking = await service.get_owned(db, providers, store, user, booking_id)
    return ok(service.booking_out(booking, await service.ticket_of(db, booking)))


@router.post("/{booking_id}/cancel", response_model=Envelope[BookingOut])
async def cancel(booking_id: uuid.UUID, user: CurrentUser, db: DbSession, providers: ProvidersDep, store: StoreDep):
    """Phase 3: releases an unpaid hold. Paid bookings get cancellation + refund in Phase 7."""
    return ok(service.booking_out(await service.cancel_hold(db, providers, store, user, booking_id)))
