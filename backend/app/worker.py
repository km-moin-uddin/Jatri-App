"""Background worker (ARQ on Redis). Run: `arq app.worker.WorkerSettings`.

Jobs: release unpaid holds (every minute), send/follow refunds (every 5 minutes), journey reminders
24 h and 3 h before departure (every 10 minutes), flight schedule changes and price alerts (every 30 minutes)."""

from arq import cron
from arq.connections import RedisSettings

from app.core.config import get_settings
from app.db.session import SessionLocal
from app.payments import get_gateway
from app.providers.registry import default_providers
from app.services.booking import sweep_expired_holds
from app.services.cache import get_store
from app.services.cancellation import process_refunds
from app.services.engagement import check_price_alerts
from app.services.journeys import check_flight_changes, send_journey_reminders


async def expire_holds(ctx) -> int:
    async with SessionLocal() as db:
        return await sweep_expired_holds(db, default_providers(), get_store())


async def refunds(ctx) -> int:
    settings = get_settings()
    async with SessionLocal() as db:
        return await process_refunds(db, get_gateway(settings), settings)


async def reminders(ctx) -> int:
    async with SessionLocal() as db:
        return await send_journey_reminders(db, get_settings())


async def price_alerts(ctx) -> int:
    async with SessionLocal() as db:
        return await check_price_alerts(db, default_providers(), get_settings())


async def flight_changes(ctx) -> int:
    async with SessionLocal() as db:
        return await check_flight_changes(db, default_providers(), get_settings())


class WorkerSettings:
    functions = [expire_holds, refunds, reminders, flight_changes, price_alerts]
    cron_jobs = [
        cron(expire_holds, second=0),  # every minute
        cron(refunds, minute=set(range(0, 60, 5)), second=10),
        cron(reminders, minute=set(range(0, 60, 10)), second=20),
        cron(flight_changes, minute={0, 30}, second=30),
        cron(price_alerts, minute={15, 45}, second=40),
    ]
    redis_settings = RedisSettings.from_dsn(get_settings().redis_url or "redis://localhost:6379/0")
