"""Cancellation & refunds (spec: "clear refund amount preview based on fare rules").

The app first asks for a **quote** (what comes back, what is kept and why), the user confirms, and
the server checks the quote again before cancelling with the provider. Refunds then go to the gateway
through a background job and are followed until the money is back.

Policies (mock providers; a real provider's own rules replace these):
- Bus: 24 h+ before departure 10% fee, 12–24 h 50% fee, under 12 h no cancellation.
- Train (Bangladesh Railway-like): 48 h+ 10%, 24–48 h 25%, 12–24 h 50%, under 12 h no cancellation.
- Flight: the fare rules: refundable fares keep the refund fee per passenger; non-refundable fares
  can be cancelled but nothing comes back; no changes inside the fare's deadline.
- The service charge is never refunded."""

import logging
import uuid
from dataclasses import dataclass
from datetime import datetime, timedelta

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import Settings
from app.core.envelope import AppError
from app.core.security import now
from app.core.text import bn_digits
from app.models import Booking, Payment, Refund, User
from app.payments.base import GatewayError
from app.providers.base import ProviderError
from app.providers.registry import Providers
from app.services import notify
from app.services.booking import BookingErrors, cancel_hold, get_owned
from app.services.cache import KeyValueStore

log = logging.getLogger("jatri.refunds")
MAX_REFUND_ATTEMPTS = 5

# (hours before departure at least, fee as a fraction of the refundable fare)
_BUS_POLICY = [(24, 0.10), (12, 0.50)]
_TRAIN_POLICY = [(48, 0.10), (24, 0.25), (12, 0.50)]


@dataclass(frozen=True)
class Quote:
    cancellable: bool
    paid: int  # what the customer paid
    refund: int  # what goes back
    fee: int  # cancellation fee kept
    non_refundable: int  # service charge (never refunded)
    hours_left: float
    reason: str | None  # why not cancellable / why nothing comes back
    message_en: str
    message_bn: str
    deadline: datetime | None  # last moment cancelling is possible


class CancelErrors:
    @staticmethod
    def not_cancellable(q: Quote) -> AppError:
        return AppError(409, q.reason or "not_cancellable", q.message_en, bn_digits(q.message_bn))

    @staticmethod
    def refund_changed(amount: int) -> AppError:
        taka = f"{amount // 100:,}"
        return AppError(
            409,
            "refund_changed",
            f"The refund has changed. You would now get ৳ {taka}.",
            bn_digits(f"রিফান্ডের পরিমাণ বদলেছে। এখন আপনি পাবেন ৳ {taka}।"),
        )


def _aware(dt: datetime) -> datetime:
    return dt if dt.tzinfo else dt.replace(tzinfo=now().tzinfo)


def _first_departure(b: Booking) -> datetime:
    return min(_aware(s.depart_at) for s in b.segments)


def _no(q_reason: str, en: str, bn: str, paid: int, hours: float) -> Quote:
    return Quote(False, paid, 0, 0, 0, hours, q_reason, en, bn, None)


def quote(b: Booking, at: datetime | None = None) -> Quote:
    at = at or now()
    paid = b.total_amount
    hours = (_first_departure(b) - at).total_seconds() / 3600
    if b.status == "held":
        return Quote(
            True,
            0,
            0,
            0,
            0,
            hours,
            None,
            "Nothing has been paid yet; the seats are simply released.",
            "এখনো কোনো টাকা দেওয়া হয়নি; সিটগুলো ছেড়ে দেওয়া হবে।",
            None,
        )
    if b.status != "ticketed":
        return _no("not_cancellable", "This booking can't be cancelled.", "এই বুকিংটি বাতিল করা যাবে না।", paid, hours)
    if hours <= 0:
        return _no("already_departed", "This trip has already started.", "এই যাত্রা ইতিমধ্যে শুরু হয়ে গেছে।", paid, hours)

    service = b.service_charge
    refundable = max(0, paid - service)  # base fare + taxes - discount
    if b.mode == "flight":
        rules = (b.details or {}).get("rules") or {}
        deadline_h = rules.get("deadline_hours", 3)
        deadline = _first_departure(b) - timedelta(hours=deadline_h)
        if hours < deadline_h:
            return _no(
                "too_late",
                f"Flights on this fare can only be cancelled up to {deadline_h} hours before departure.",
                f"এই ভাড়ার ফ্লাইট যাত্রার {deadline_h} ঘণ্টা আগে পর্যন্ত বাতিল করা যায়।",
                paid,
                hours,
            )
        if not rules.get("refundable"):
            return Quote(
                True,
                paid,
                0,
                refundable,
                service,
                hours,
                "non_refundable",
                "This fare is non-refundable: you can cancel, but no money comes back.",
                "এই ভাড়া রিফান্ডযোগ্য নয়: বাতিল করা যাবে, কিন্তু কোনো টাকা ফেরত আসবে না।",
                deadline,
            )
        fee = min(refundable, (rules.get("refund_fee") or 0) * len(b.passengers))
        return Quote(
            True,
            paid,
            refundable - fee,
            fee,
            service,
            hours,
            None,
            "Refund fee per passenger from the fare rules; the service charge is not refunded.",
            "ভাড়ার নিয়ম অনুযায়ী প্রতি যাত্রী রিফান্ড ফি কাটা হবে; সার্ভিস চার্জ ফেরত হয় না।",
            deadline,
        )

    policy = _TRAIN_POLICY if b.mode == "train" else _BUS_POLICY
    last_h = policy[-1][0]
    deadline = _first_departure(b) - timedelta(hours=last_h)
    for min_hours, fraction in policy:
        if hours >= min_hours:
            fee = round(refundable * fraction / 100) * 100  # whole taka
            pct = int(fraction * 100)
            return Quote(
                True,
                paid,
                refundable - fee,
                fee,
                service,
                hours,
                None,
                f"{pct}% cancellation fee ({int(min_hours)}+ hours before departure); the service charge is not refunded.",
                f"{pct}% বাতিল ফি (যাত্রার {int(min_hours)}+ ঘণ্টা আগে); সার্ভিস চার্জ ফেরত হয় না।",
                deadline,
            )
    return _no(
        "too_late",
        f"Tickets can be cancelled up to {last_h} hours before departure.",
        f"যাত্রার {last_h} ঘণ্টা আগে পর্যন্ত টিকিট বাতিল করা যায়।",
        paid,
        hours,
    )


async def _paid_payment(db: AsyncSession, booking: Booking) -> Payment | None:
    return await db.scalar(
        select(Payment).where(Payment.booking_id == booking.id, Payment.status.in_(("success", "refund_pending"))).limit(1)
    )


async def cancel(
    db: AsyncSession,
    providers: Providers,
    store: KeyValueStore,
    settings: Settings,
    gateway,
    user: User,
    booking_id: uuid.UUID,
    expected_refund: int | None,
) -> Booking:
    booking = await get_owned(db, providers, store, user, booking_id)
    if booking.status in ("cancelled", "refunded"):
        return booking
    if booking.status == "held":
        return await cancel_hold(db, providers, store, user, booking_id)
    q = quote(booking)
    if not q.cancellable:
        raise CancelErrors.not_cancellable(q)
    if expected_refund is not None and expected_refund != q.refund:
        raise CancelErrors.refund_changed(q.refund)
    payment = await _paid_payment(db, booking)
    if payment is None:
        raise BookingErrors.not_cancellable()
    try:
        await providers.get(booking.provider).cancel(booking.provider_booking_ref or "")
    except ProviderError as e:
        raise AppError(409, e.code, e.message_en, e.message_bn) from e

    booking.status = "cancelled"
    booking.details = {**(booking.details or {}), "cancelled_at": now().isoformat()}
    if q.refund > 0:
        db.add(
            Refund(
                booking_id=booking.id,
                payment_id=payment.id,
                amount=q.refund,
                fee=q.fee,
                reason="user_cancel",
                status="pending",
                attempts=0,
            )
        )
    await notify.booking_cancelled(db, settings, booking, q.refund)
    await db.commit()
    await process_refunds(db, gateway, settings, booking_id=booking.id)  # usually done at once; the job retries
    await db.refresh(booking)
    return booking


async def refund_of(db: AsyncSession, booking: Booking) -> Refund | None:
    return await db.scalar(select(Refund).where(Refund.booking_id == booking.id).order_by(Refund.created_at.desc()).limit(1))


async def process_refunds(db: AsyncSession, gateway, settings: Settings, booking_id: uuid.UUID | None = None) -> int:
    """Background job: open refunds for provider-refused payments, send pending refunds to the
    gateway, and follow those in progress. Returns how many refunds finished."""
    # Paid but the provider refused (Phase 4): refund everything.
    stmt = select(Payment).where(Payment.status == "refund_pending")
    if booking_id:
        stmt = stmt.where(Payment.booking_id == booking_id)
    for p in (await db.scalars(stmt)).all():
        if await db.scalar(select(Refund.id).where(Refund.payment_id == p.id)) is None:
            db.add(
                Refund(
                    booking_id=p.booking_id,
                    payment_id=p.id,
                    amount=p.amount,
                    fee=0,
                    reason="provider_refused",
                    status="pending",
                    attempts=0,
                )
            )
    await db.flush()

    stmt = select(Refund).where(Refund.status.in_(("pending", "processing")))
    if booking_id:
        stmt = stmt.where(Refund.booking_id == booking_id)
    finished = 0
    for r in (await db.scalars(stmt)).all():
        payment = await db.get(Payment, r.payment_id)
        try:
            if r.status == "pending":
                r.attempts += 1
                result = await gateway.refund(payment.gateway_txn_id or "", r.amount, f"Jatri refund {r.reason}")
                r.gateway_ref = result.ref
                r.status = "refunded" if result.done else "processing"
            else:
                r.status = {"refunded": "refunded", "failed": "failed"}.get(
                    await gateway.refund_status(r.gateway_ref or ""), "processing"
                )
        except (GatewayError, Exception) as e:  # noqa: BLE001 - network or gateway refusal: try again later
            log.warning("refund %s attempt %s failed: %s", r.id, r.attempts, type(e).__name__)
            if r.attempts >= MAX_REFUND_ATTEMPTS:
                r.status = "failed"
                log.error("ALERT refund %s for booking %s failed %s times: pay back by hand", r.id, r.booking_id, r.attempts)
        if r.status == "refunded":
            r.processed_at = now()
            payment.status = "refunded"
            booking = await db.get(Booking, r.booking_id)
            booking.status = "refunded"
            await notify.refund_processed(db, settings, booking, r.amount)
            finished += 1
    await db.commit()
    return finished
