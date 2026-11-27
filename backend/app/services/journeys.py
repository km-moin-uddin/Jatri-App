"""Background jobs about upcoming journeys: reminders 24 h and 3 h before departure, and flight
schedule changes (delays) passed on to the traveller."""

from datetime import timedelta

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import Settings
from app.core.security import now
from app.models import Booking, BookingSegment
from app.providers.registry import Providers
from app.services import notify

REMINDERS = (24, 3)  # hours before departure


def _aware(dt):
    return dt if dt.tzinfo else dt.replace(tzinfo=now().tzinfo)


async def _upcoming(db: AsyncSession, within: timedelta) -> list[Booking]:
    t = now()
    ids = select(BookingSegment.booking_id).where(BookingSegment.depart_at > t, BookingSegment.depart_at <= t + within)
    return list((await db.scalars(select(Booking).where(Booking.status == "ticketed", Booking.id.in_(ids)))).all())


async def send_journey_reminders(db: AsyncSession, settings: Settings) -> int:
    """Each booking gets each reminder once; a booking made 5 h ahead only gets the 3 h one."""
    sent = 0
    for b in await _upcoming(db, timedelta(hours=max(REMINDERS))):
        left = (min(_aware(s.depart_at) for s in b.segments) - now()).total_seconds() / 3600
        if left <= 0:
            continue
        done = set((b.details or {}).get("reminders", []))
        due = [h for h in REMINDERS if left <= h and str(h) not in done]
        if not due:
            continue
        hours = min(due)  # the most urgent one; the earlier ones are no longer useful
        await notify.journey_reminder(db, settings, b, hours)
        b.details = {**(b.details or {}), "reminders": sorted(done | {str(h) for h in REMINDERS if h >= hours})}
        sent += 1
    await db.commit()
    return sent


async def check_flight_changes(db: AsyncSession, providers: Providers, settings: Settings) -> int:
    """Ask the airline about each upcoming ticketed flight; move the times and tell the traveller."""
    changed = 0
    for b in await _upcoming(db, timedelta(days=30)):
        if b.mode != "flight":
            continue
        delay = await providers.get(b.provider).get_delay_minutes(b.provider_booking_ref or "")
        known = (b.details or {}).get("delay_minutes", 0)
        if delay == known:
            continue
        shift = timedelta(minutes=delay - known)
        first = b.segments[0]
        # The delay moves the first journey (its connections are protected by the airline).
        for s in b.segments:
            if (s.extra or {}).get("slice_index", 0) == (first.extra or {}).get("slice_index", 0):
                s.depart_at = _aware(s.depart_at) + shift
                s.arrive_at = _aware(s.arrive_at) + shift
        b.details = {**(b.details or {}), "delay_minutes": delay}
        await notify.flight_changed(db, settings, b, first.number, delay)
        changed += 1
    await db.commit()
    return changed
