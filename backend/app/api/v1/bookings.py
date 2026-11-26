import uuid
from typing import Annotated

from fastapi import APIRouter, Header

from app.api.deps import AppSettings, CurrentUser, DbSession
from app.api.v1.buses import BusProviderDep, StoreDep
from app.core.envelope import ok
from app.schemas.booking import BookingCreateIn, BookingOut
from app.schemas.common import Envelope
from app.services import booking as service

router = APIRouter(prefix="/bookings", tags=["bookings"])


@router.post("", response_model=Envelope[BookingOut])
async def create(
    body: BookingCreateIn,
    user: CurrentUser,
    db: DbSession,
    provider: BusProviderDep,
    store: StoreDep,
    settings: AppSettings,
    idempotency_key: Annotated[str | None, Header(max_length=64)] = None,
):
    """Holds the seats for `hold_minutes` (status `held`). Send an `Idempotency-Key` header so a
    retried request returns the same booking instead of holding seats twice."""
    booking = await service.create_bus_hold(db, provider, store, settings, user, body, idempotency_key)
    return ok(service.booking_out(booking, await service.ticket_of(db, booking)))


@router.get("", response_model=Envelope[list[BookingOut]])
async def list_bookings(user: CurrentUser, db: DbSession, provider: BusProviderDep, store: StoreDep):
    return ok(
        [service.booking_out(b, await service.ticket_of(db, b)) for b in await service.list_owned(db, provider, store, user)]
    )


@router.get("/{booking_id}", response_model=Envelope[BookingOut])
async def get(booking_id: uuid.UUID, user: CurrentUser, db: DbSession, provider: BusProviderDep, store: StoreDep):
    booking = await service.get_owned(db, provider, store, user, booking_id)
    return ok(service.booking_out(booking, await service.ticket_of(db, booking)))


@router.post("/{booking_id}/cancel", response_model=Envelope[BookingOut])
async def cancel(booking_id: uuid.UUID, user: CurrentUser, db: DbSession, provider: BusProviderDep, store: StoreDep):
    """Phase 3: releases an unpaid hold. Paid bookings get cancellation + refund in Phase 7."""
    return ok(service.booking_out(await service.cancel_hold(db, provider, store, user, booking_id)))
