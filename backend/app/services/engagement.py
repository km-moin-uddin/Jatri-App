"""Saved travellers and price alerts."""

import logging
import uuid
from datetime import date

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import Settings
from app.core.envelope import AppError
from app.models import PriceAlert, SavedTraveller, User
from app.providers.base import PaxCount, ProviderError, SliceQuery
from app.providers.mock_train import SALE_DAYS
from app.providers.registry import Providers
from app.schemas.engagement import PriceAlertIn, TravellerIn, TravellerOut
from app.services import notify
from app.services.offers import today_bd

log = logging.getLogger("jatri.alerts")
MAX_TRAVELLERS = 20
MAX_ALERTS = 10


def _age(born: date, on: date) -> int:
    return on.year - born.year - ((on.month, on.day) < (born.month, born.day))


def traveller_out(t: SavedTraveller) -> TravellerOut:
    kind = None
    if t.date_of_birth:
        age = _age(t.date_of_birth, today_bd())
        kind = "adult" if age >= 12 else "child" if age >= 2 else "infant"
    return TravellerOut(
        id=t.id,
        full_name=t.full_name,
        given_name=t.given_name,
        surname=t.surname,
        gender=t.gender,
        date_of_birth=t.date_of_birth,
        nationality=t.nationality,
        nid_number=t.nid_number,
        passport_no=t.passport_no,
        passport_expiry=t.passport_expiry,
        type=kind,
    )


def _not_found(what_en: str, what_bn: str) -> AppError:
    return AppError(404, "not_found", f"{what_en} not found.", f"{what_bn} পাওয়া যায়নি।")


async def list_travellers(db: AsyncSession, user: User) -> list[SavedTraveller]:
    return list(
        (
            await db.scalars(select(SavedTraveller).where(SavedTraveller.user_id == user.id).order_by(SavedTraveller.created_at))
        ).all()
    )


async def save_traveller(
    db: AsyncSession, user: User, body: TravellerIn, traveller_id: uuid.UUID | None = None
) -> SavedTraveller:
    if traveller_id is None:
        count = await db.scalar(select(func.count()).where(SavedTraveller.user_id == user.id))
        if (count or 0) >= MAX_TRAVELLERS:
            raise AppError(409, "too_many_travellers", "You can save up to 20 travellers.", "সর্বোচ্চ ২০ জন যাত্রী সংরক্ষণ করা যায়।")
        t = SavedTraveller(user_id=user.id)
        db.add(t)
    else:
        t = await db.get(SavedTraveller, traveller_id)
        if t is None or t.user_id != user.id:
            raise _not_found("Traveller", "যাত্রী")
    for field, value in body.model_dump().items():
        setattr(t, field, " ".join(value.split()) if isinstance(value, str) and field.endswith("name") else value)
    await db.commit()
    await db.refresh(t)
    return t


async def delete_traveller(db: AsyncSession, user: User, traveller_id: uuid.UUID) -> None:
    t = await db.get(SavedTraveller, traveller_id)
    if t is None or t.user_id != user.id:
        raise _not_found("Traveller", "যাত্রী")
    await db.delete(t)
    await db.commit()


# ---------------------------------------------------------------- price alerts


async def lowest_price(providers: Providers, mode: str, from_id: str, to_id: str, on: date) -> int | None:
    """Lowest fare per person (one adult, one way, cheapest class/cabin) or None if nothing runs."""
    try:
        if mode == "bus":
            trips = await providers.bus.search(from_id, to_id, on, None)
            return min((t.fare_paisa for t in trips), default=None)
        if mode == "train":
            if (on - today_bd()).days > SALE_DAYS:
                return None  # not on sale yet
            trips = await providers.train.search(from_id, to_id, on, None)
            return min((c.fare_paisa for t in trips for c in t.classes), default=None)
        offers = await providers.flight.search([SliceQuery(from_id, to_id, on)], PaxCount(1, 0, 0), "economy")
        return min((o.total for o in offers), default=None)
    except ProviderError:
        return None


async def create_alert(db: AsyncSession, providers: Providers, user: User, body: PriceAlertIn) -> PriceAlert:
    if body.date < today_bd():
        raise AppError(422, "date_passed", "Choose a date from today.", "আজ বা পরের কোনো তারিখ বেছে নিন।")
    active = await db.scalar(select(func.count()).where(PriceAlert.user_id == user.id, PriceAlert.is_active.is_(True)))
    if (active or 0) >= MAX_ALERTS:
        raise AppError(409, "too_many_alerts", "You can keep up to 10 price alerts.", "সর্বোচ্চ ১০টি ভাড়া-সতর্কতা রাখা যায়।")
    alert = PriceAlert(user_id=user.id, **body.model_dump(), is_active=True)
    alert.last_price = await lowest_price(providers, body.mode, body.from_id, body.to_id, body.date)
    db.add(alert)
    await db.commit()
    await db.refresh(alert)
    return alert


async def list_alerts(db: AsyncSession, user: User) -> list[PriceAlert]:
    return list((await db.scalars(select(PriceAlert).where(PriceAlert.user_id == user.id).order_by(PriceAlert.date))).all())


async def delete_alert(db: AsyncSession, user: User, alert_id: uuid.UUID) -> None:
    a = await db.get(PriceAlert, alert_id)
    if a is None or a.user_id != user.id:
        raise _not_found("Price alert", "ভাড়া-সতর্কতা")
    await db.delete(a)
    await db.commit()


async def check_price_alerts(db: AsyncSession, providers: Providers, settings: Settings) -> int:
    """Background job: refresh each active alert's lowest fare; tell the user once per new low at
    or below their target. Alerts for past dates switch off."""
    told = 0
    alerts = (await db.scalars(select(PriceAlert).where(PriceAlert.is_active.is_(True)))).all()
    for a in alerts:
        if a.date < today_bd():
            a.is_active = False
            continue
        price = await lowest_price(providers, a.mode, a.from_id, a.to_id, a.date)
        if price is None:
            continue
        a.last_price = price
        if price <= a.target_price and (a.notified_price is None or price < a.notified_price):
            a.notified_price = price
            await notify.price_drop(db, settings, a, price)
            told += 1
    await db.commit()
    return told
