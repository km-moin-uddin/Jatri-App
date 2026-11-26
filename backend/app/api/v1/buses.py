from typing import Annotated

from fastapi import APIRouter, Depends

from app.api.deps import AppSettings
from app.core.envelope import ok
from app.providers.mock_bus import MockBusProvider, get_bus_provider
from app.schemas.bus import BusSearchIn, BusTripOut, SeatMapOut
from app.schemas.common import Envelope
from app.services import booking as service
from app.services.cache import KeyValueStore, get_store

router = APIRouter(prefix="/buses", tags=["bus"])

BusProviderDep = Annotated[MockBusProvider, Depends(get_bus_provider)]
StoreDep = Annotated[KeyValueStore, Depends(get_store)]


@router.post("/search", response_model=Envelope[list[BusTripOut]])
async def search(body: BusSearchIn, provider: BusProviderDep, store: StoreDep, settings: AppSettings):
    """Results are cached for a few minutes; live price and seats are re-checked at booking time."""
    trips = await service.search_buses(provider, store, settings, body.from_id, body.to_id, body.date, body.coach_type)
    return ok(trips)


@router.get("/{trip_id}/seats", response_model=Envelope[SeatMapOut])
async def seats(trip_id: str, provider: BusProviderDep, store: StoreDep, settings: AppSettings):
    return ok(await service.seat_map(provider, store, settings, trip_id))
