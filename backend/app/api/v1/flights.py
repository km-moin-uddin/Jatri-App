from fastapi import APIRouter

from app.api.deps import AppSettings
from app.api.v1.buses import StoreDep
from app.core.envelope import ok
from app.providers.registry import ProvidersDep
from app.schemas.common import Envelope
from app.schemas.flight import FlightOfferOut, FlightSearchIn
from app.services import flight as service

router = APIRouter(prefix="/flights", tags=["flight"])


@router.post("/search", response_model=Envelope[list[FlightOfferOut]])
async def search(body: FlightSearchIn, providers: ProvidersDep, store: StoreDep, settings: AppSettings):
    """One-way (1 slice), round trip (2) or multi-city (up to 4). Cached a few minutes; prices are
    re-checked live on the offer details screen and again at booking time."""
    return ok(await service.search_flights(providers, store, settings, body))


@router.get("/{offer_id}", response_model=Envelope[FlightOfferOut])
async def details(offer_id: str, providers: ProvidersDep, settings: AppSettings):
    """Live price, fare rules and baggage for one offer. `offer_expired` (409) if it's gone."""
    return ok(service.offer_out(await service.live_offer(providers, offer_id), settings))
