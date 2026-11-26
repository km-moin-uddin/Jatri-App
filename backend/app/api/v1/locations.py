from typing import Literal

from fastapi import APIRouter, Query
from sqlalchemy import or_, select

from app.api.deps import DbSession
from app.core.envelope import ok
from app.models import Location
from app.schemas.common import Envelope
from app.schemas.location import LocationOut

router = APIRouter(prefix="/locations", tags=["locations"])


@router.get("", response_model=Envelope[list[LocationOut]])
async def list_locations(
    db: DbSession,
    type: Literal["airport", "bus_city", "station"] | None = None,
    q: str | None = Query(default=None, max_length=50),
):
    stmt = select(Location).order_by(Location.is_popular.desc(), Location.name_en)
    if type:
        stmt = stmt.where(Location.type == type)
    if q:
        like = f"%{q.strip()}%"
        stmt = stmt.where(
            or_(
                Location.code.ilike(like),
                Location.name_en.ilike(like),
                Location.city_en.ilike(like),
                Location.name_bn.like(like),
                Location.city_bn.like(like),
            )
        )
    rows = (await db.scalars(stmt)).all()
    return ok([LocationOut(id=r.key, **{k: getattr(r, k) for k in LocationOut.model_fields if k != "id"}) for r in rows])
