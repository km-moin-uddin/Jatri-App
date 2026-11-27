"""In-app inbox + push for booking events (spec: confirmed, payment failed, cancelled, refunded,
journey reminders, flight changes). Sending never breaks the booking flow: failures are logged."""

import logging
import uuid
from datetime import UTC, timedelta, timezone

from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import Settings
from app.core.security import now
from app.core.text import bn_digits
from app.models import Booking, DeviceToken, Notification, User
from app.services.push import PushSender, get_push_sender
from app.services.ticket import _place_names

log = logging.getLogger("jatri.notify")
_BD = timezone(timedelta(hours=6))


def route(booking: Booking) -> tuple[str, str]:
    """("Dhaka → Cox's Bazar", "ঢাকা → কক্সবাজার") for the first journey."""
    names = _place_names()
    first = booking.segments[0]
    last = [s for s in booking.segments if (s.extra or {}).get("slice_index", 0) == 0][-1]
    a = names.get(first.from_id, (first.from_id, first.from_id))
    b = names.get(last.to_id, (last.to_id, last.to_id))
    return f"{a[0]} → {b[0]}", f"{a[1]} → {b[1]}"


def when(booking: Booking) -> str:
    dep = booking.segments[0].depart_at
    if dep.tzinfo is None:
        dep = dep.replace(tzinfo=UTC)
    return dep.astimezone(_BD).strftime("%d %b, %H:%M")


def _taka(paisa: int) -> str:
    return f"৳ {paisa // 100:,}"


async def notify(
    db: AsyncSession,
    settings: Settings,
    user_id: uuid.UUID | None,
    type: str,
    en: tuple[str, str],
    bn: tuple[str, str],
    data: dict | None = None,
    push: PushSender | None = None,
) -> Notification | None:
    """Adds to the inbox (caller commits) and pushes to the user's devices in their language."""
    if user_id is None:
        return None
    n = Notification(
        created_at=now(),  # exact time: several can be added in one transaction, and order matters
        user_id=user_id,
        type=type,
        title_en=en[0],
        body_en=en[1],
        title_bn=bn_digits(bn[0]),
        body_bn=bn_digits(bn[1]),
        data=data or {},
        is_read=False,
    )
    db.add(n)
    try:
        user = await db.get(User, user_id)
        tokens = (await db.scalars(select(DeviceToken).where(DeviceToken.user_id == user_id))).all()
        if tokens:
            sender = push or get_push_sender(settings)
            title, body = (n.title_bn, n.body_bn) if (user and user.language == "bn") else en
            payload = {k: str(v) for k, v in (data or {}).items()} | {"type": type}
            dead = [t.token for t in tokens if not await sender.send(t.token, title, body, payload)]
            if dead:
                await db.execute(delete(DeviceToken).where(DeviceToken.token.in_(dead)))
    except Exception:  # noqa: BLE001 - a push problem must never undo a booking
        log.exception("push failed for user %s (%s)", user_id, type)
    return n


# ---------------------------------------------------------------- messages


async def booking_confirmed(db, settings, b: Booking) -> None:
    r_en, r_bn = route(b)
    await notify(
        db,
        settings,
        b.user_id,
        "booking_confirmed",
        ("Ticket confirmed 🎉", f"{r_en}, {when(b)}. Booking {b.booking_ref}. Your e-ticket is in My Trips."),
        ("টিকিট কনফার্ম হয়েছে 🎉", f"{r_bn}, {when(b)}। বুকিং {b.booking_ref}। ই-টিকিট আমার যাত্রায় আছে।"),
        {"booking_id": str(b.id)},
    )


async def payment_failed(db, settings, b: Booking) -> None:
    await notify(
        db,
        settings,
        b.user_id,
        "payment_failed",
        ("Payment didn't go through", f"Booking {b.booking_ref} is still held. You can try paying again."),
        ("পেমেন্ট সম্পন্ন হয়নি", f"বুকিং {b.booking_ref} এখনো সংরক্ষিত আছে। আবার পেমেন্ট করতে পারেন।"),
        {"booking_id": str(b.id)},
    )


async def booking_failed(db, settings, b: Booking) -> None:
    await notify(
        db,
        settings,
        b.user_id,
        "booking_failed",
        (
            "We couldn't issue your ticket",
            f"Booking {b.booking_ref}: the operator refused after payment. Your money is being refunded.",
        ),
        ("টিকিট ইস্যু করা যায়নি", f"বুকিং {b.booking_ref}: পেমেন্টের পর অপারেটর টিকিট দেয়নি। আপনার টাকা ফেরত দেওয়া হচ্ছে।"),
        {"booking_id": str(b.id)},
    )


async def booking_cancelled(db, settings, b: Booking, refund: int) -> None:
    r_en, r_bn = route(b)
    tail_en = f" {_taka(refund)} will be refunded to your payment account." if refund else " No refund applies to this fare."
    tail_bn = f" {_taka(refund)} আপনার পেমেন্ট অ্যাকাউন্টে ফেরত যাবে।" if refund else " এই ভাড়ায় কোনো রিফান্ড নেই।"
    await notify(
        db,
        settings,
        b.user_id,
        "booking_cancelled",
        ("Booking cancelled", f"{r_en} ({b.booking_ref}) is cancelled.{tail_en}"),
        ("বুকিং বাতিল হয়েছে", f"{r_bn} ({b.booking_ref}) বাতিল হয়েছে।{tail_bn}"),
        {"booking_id": str(b.id)},
    )


async def refund_processed(db, settings, b: Booking, amount: int) -> None:
    await notify(
        db,
        settings,
        b.user_id,
        "refund_processed",
        ("Refund sent", f"{_taka(amount)} for booking {b.booking_ref} has been refunded."),
        ("রিফান্ড পাঠানো হয়েছে", f"বুকিং {b.booking_ref}-এর {_taka(amount)} ফেরত দেওয়া হয়েছে।"),
        {"booking_id": str(b.id)},
    )


async def journey_reminder(db, settings, b: Booking, hours: int) -> None:
    r_en, r_bn = route(b)
    hint_en = {
        "flight": "Reach the airport 2 hours early.",
        "train": "Carry your NID.",
        "bus": "Reach the counter 15 minutes early.",
    }[b.mode]
    hint_bn = {"flight": "২ ঘণ্টা আগে বিমানবন্দরে পৌঁছান।", "train": "এনআইডি সাথে রাখুন।", "bus": "১৫ মিনিট আগে কাউন্টারে পৌঁছান।"}[b.mode]
    await notify(
        db,
        settings,
        b.user_id,
        "journey_reminder",
        (f"Your trip is in {hours} hours", f"{r_en} at {when(b)}. {hint_en}"),
        (f"আপনার যাত্রা {hours} ঘণ্টা পর", f"{r_bn}, {when(b)}। {hint_bn}"),
        {"booking_id": str(b.id), "hours": hours},
    )


async def flight_changed(db, settings, b: Booking, number: str, minutes: int) -> None:
    await notify(
        db,
        settings,
        b.user_id,
        "flight_changed",
        (f"Flight {number} is delayed", f"New departure: {when(b)} ({minutes} min later). Booking {b.booking_ref}."),
        (f"ফ্লাইট {number} দেরিতে ছাড়বে", f"নতুন সময়: {when(b)} ({minutes} মিনিট পরে)। বুকিং {b.booking_ref}।"),
        {"booking_id": str(b.id)},
    )
