from typing import Annotated

from fastapi import APIRouter, Query

from app.api.deps import AppSettings
from app.api.v1.buses import StoreDep
from app.core.envelope import ok
from app.core.ratelimit import limit
from app.providers.registry import ProvidersDep
from app.schemas.common import Envelope
from app.schemas.train import TrainClass, TrainSearchIn, TrainSeatMapOut, TrainTripOut
from app.services import train as service

router = APIRouter(prefix="/trains", tags=["train"])


@router.post("/search", response_model=Envelope[list[TrainTripOut]], dependencies=[limit("search", 60, 60)])
async def search(body: TrainSearchIn, providers: ProvidersDep, store: StoreDep, settings: AppSettings):
    """Trains for one day, with seats left and fare per class. Only dates up to 10 days ahead
    (`outside_sale_window` otherwise); trains on their weekly off day are left out."""
    return ok(await service.search_trains(providers, store, settings, body.from_id, body.to_id, body.date, body.travel_class))


@router.get("/{trip_id}/seats", response_model=Envelope[TrainSeatMapOut])
async def seats(
    trip_id: str, travel_class: Annotated[TrainClass, Query()], providers: ProvidersDep, store: StoreDep, settings: AppSettings
):
    """Coaches of one class with their seat maps; seats someone else is paying for show as `held`."""
    return ok(await service.seat_map(providers, store, settings, trip_id, travel_class))
