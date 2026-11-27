"""Background worker (ARQ on Redis). Run: `arq app.worker.WorkerSettings`.

Phase 4 jobs: release unpaid holds every minute. Ticket e-mails/SMS, journey reminders and
refund processing join in Phase 7."""

from arq import cron
from arq.connections import RedisSettings

from app.core.config import get_settings
from app.db.session import SessionLocal
from app.providers.registry import default_providers
from app.services.booking import sweep_expired_holds
from app.services.cache import get_store


async def expire_holds(ctx) -> int:
    async with SessionLocal() as db:
        return await sweep_expired_holds(db, default_providers(), get_store())


class WorkerSettings:
    functions = [expire_holds]
    cron_jobs = [cron(expire_holds, second=0)]  # every minute
    redis_settings = RedisSettings.from_dsn(get_settings().redis_url or "redis://localhost:6379/0")
