import uuid
from typing import Annotated

from fastapi import APIRouter, Query

from app.api.deps import CurrentUser, DbSession
from app.api.v1.buses import StoreDep
from app.core.envelope import ok
from app.providers.registry import ProvidersDep
from app.schemas.booking import BookingOut
from app.schemas.common import Envelope
from app.schemas.engagement import OfferOut, PriceAlertIn, PriceAlertOut, PromoIn, TravellerIn, TravellerOut
from app.services import booking as booking_service
from app.services import engagement as service
from app.services import offers as offers_service

router = APIRouter(tags=["offers & profile"])


@router.get("/offers", response_model=Envelope[list[OfferOut]])
async def offers(db: DbSession, mode: Annotated[str | None, Query(pattern="^(flight|bus|train)$")] = None):
    """Running offers (public). `mode` keeps those valid for that kind of ticket."""
    return ok([OfferOut.model_validate(o, from_attributes=True) for o in await offers_service.active_offers(db, mode)])


@router.post("/bookings/{booking_id}/promo", response_model=Envelope[BookingOut])
async def apply_promo(
    booking_id: uuid.UUID, body: PromoIn, user: CurrentUser, db: DbSession, providers: ProvidersDep, store: StoreDep
):
    """Applies a code to a held booking: `discount` and `total_amount` change at once. One code per
    booking (a new one replaces the old). Codes may need a payment method (`promo_method` at pay time)."""
    booking = await booking_service.get_owned(db, providers, store, user, booking_id)
    booking = await offers_service.apply(db, user, booking, body.code)
    return ok(await booking_service.full_out(db, booking))


@router.delete("/bookings/{booking_id}/promo", response_model=Envelope[BookingOut])
async def remove_promo(booking_id: uuid.UUID, user: CurrentUser, db: DbSession, providers: ProvidersDep, store: StoreDep):
    booking = await booking_service.get_owned(db, providers, store, user, booking_id)
    booking = await offers_service.remove(db, booking)
    return ok(await booking_service.full_out(db, booking))


@router.get("/me/travellers", response_model=Envelope[list[TravellerOut]])
async def travellers(user: CurrentUser, db: DbSession):
    return ok([service.traveller_out(t) for t in await service.list_travellers(db, user)])


@router.post("/me/travellers", response_model=Envelope[TravellerOut])
async def add_traveller(body: TravellerIn, user: CurrentUser, db: DbSession):
    return ok(service.traveller_out(await service.save_traveller(db, user, body)))


@router.put("/me/travellers/{traveller_id}", response_model=Envelope[TravellerOut])
async def edit_traveller(traveller_id: uuid.UUID, body: TravellerIn, user: CurrentUser, db: DbSession):
    return ok(service.traveller_out(await service.save_traveller(db, user, body, traveller_id)))


@router.delete("/me/travellers/{traveller_id}", response_model=Envelope[None])
async def remove_traveller(traveller_id: uuid.UUID, user: CurrentUser, db: DbSession):
    await service.delete_traveller(db, user, traveller_id)
    return ok()


@router.get("/price-alerts", response_model=Envelope[list[PriceAlertOut]])
async def price_alerts(user: CurrentUser, db: DbSession):
    return ok([PriceAlertOut.model_validate(a, from_attributes=True) for a in await service.list_alerts(db, user)])


@router.post("/price-alerts", response_model=Envelope[PriceAlertOut])
async def add_price_alert(body: PriceAlertIn, user: CurrentUser, db: DbSession, providers: ProvidersDep):
    """Starts watching a route and day; `last_price` is today's lowest fare per person."""
    return ok(PriceAlertOut.model_validate(await service.create_alert(db, providers, user, body), from_attributes=True))


@router.delete("/price-alerts/{alert_id}", response_model=Envelope[None])
async def remove_price_alert(alert_id: uuid.UUID, user: CurrentUser, db: DbSession):
    await service.delete_alert(db, user, alert_id)
    return ok()
