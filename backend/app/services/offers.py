"""Offers & promo codes. A code is applied to a *held* booking (the price drops at once, the
redemption is `applied`), becomes `used` when the ticket is issued, and is `released` if the hold
expires or is cancelled, so the user can use it again."""

import uuid
from datetime import timedelta

from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.envelope import AppError
from app.core.security import now
from app.core.text import bn_digits
from app.models import Booking, Offer, Payment, PromoRedemption, User

_TZ_BD = timedelta(hours=6)
_PAYMENT_SESSION = timedelta(minutes=15)  # an open gateway page can still be paid for this long


def _taka(paisa: int) -> str:
    return f"৳ {paisa // 100:,}"


class PromoErrors:
    @staticmethod
    def _e(code: str, en: str, bn: str, status: int = 422) -> AppError:
        return AppError(status, code, en, bn_digits(bn))

    @classmethod
    def invalid(cls):
        return cls._e("promo_invalid", "This promo code doesn't exist or has ended.", "এই প্রোমো কোডটি নেই বা মেয়াদ শেষ।")

    @classmethod
    def wrong_mode(cls):
        return cls._e("promo_wrong_mode", "This code isn't valid for this kind of ticket.", "এই কোডটি এই ধরনের টিকিটে চলবে না।")

    @classmethod
    def min_amount(cls, paisa: int):
        return cls._e(
            "promo_min_amount",
            f"This code needs a fare of at least {_taka(paisa)}.",
            f"এই কোডের জন্য ভাড়া অন্তত {_taka(paisa)} হতে হবে।",
        )

    @classmethod
    def first_booking(cls):
        return cls._e("promo_first_booking", "This code is for your first booking only.", "এই কোডটি শুধু প্রথম বুকিংয়ের জন্য।")

    @classmethod
    def used_up(cls):
        return cls._e("promo_used_up", "You've already used this code.", "আপনি এই কোডটি আগেই ব্যবহার করেছেন।")

    @classmethod
    def sold_out(cls):
        return cls._e("promo_sold_out", "This offer has run out.", "এই অফারটি শেষ হয়ে গেছে।")

    @classmethod
    def not_held(cls):
        return cls._e("promo_not_allowed", "Codes can only be added before paying.", "পেমেন্টের আগেই শুধু কোড যোগ করা যায়।", 409)

    @classmethod
    def payment_open(cls):
        return cls._e(
            "payment_in_progress",
            "A payment for this booking is open. Finish it or try again in a few minutes.",
            "এই বুকিংয়ের একটি পেমেন্ট চালু আছে। সেটি শেষ করুন বা কয়েক মিনিট পর চেষ্টা করুন।",
            409,
        )

    @classmethod
    def method(cls, methods: list[str]):
        names = {"bkash": "bKash", "nagad": "Nagad", "rocket": "Rocket", "card": "card"}
        names_bn = {"bkash": "বিকাশ", "nagad": "নগদ", "rocket": "রকেট", "card": "কার্ড"}
        en = " or ".join(names.get(m, m) for m in methods)
        bn = " বা ".join(names_bn.get(m, m) for m in methods)
        return cls._e(
            "promo_method", f"Your promo code needs payment by {en}.", f"আপনার প্রোমো কোডের জন্য {bn} দিয়ে পেমেন্ট করতে হবে।", 409
        )


def today_bd():
    return (now() + _TZ_BD).date()


async def active_offers(db: AsyncSession, mode: str | None = None) -> list[Offer]:
    t = today_bd()
    rows = (
        await db.scalars(
            select(Offer).where(Offer.is_active.is_(True), Offer.valid_from <= t, Offer.valid_to >= t).order_by(Offer.code)
        )
    ).all()
    return [o for o in rows if not mode or not o.modes or mode in o.modes]


def discount_for(offer: Offer, fare: int) -> int:
    """`fare` = what the code applies to (base fare + taxes, not the service charge)."""
    if offer.discount_type == "percent":
        d = fare * offer.value // 100
        if offer.max_discount is not None:
            d = min(d, offer.max_discount)
    else:
        d = offer.value
    return max(0, min(d, fare)) // 100 * 100  # whole taka, never more than the fare


def _fare(b: Booking) -> int:
    return b.base_fare + (b.details or {}).get("taxes", 0)


async def redemption_of(db: AsyncSession, booking: Booking) -> PromoRedemption | None:
    return await db.scalar(select(PromoRedemption).where(PromoRedemption.booking_id == booking.id))


async def _check(db: AsyncSession, offer: Offer, user: User, booking: Booking) -> None:
    if offer.modes and booking.mode not in offer.modes:
        raise PromoErrors.wrong_mode()
    if _fare(booking) < offer.min_amount:
        raise PromoErrors.min_amount(offer.min_amount)
    if offer.usage_limit is not None and offer.used_count >= offer.usage_limit:
        raise PromoErrors.sold_out()
    mine = await db.scalar(
        select(func.count()).where(
            PromoRedemption.offer_id == offer.id,
            PromoRedemption.user_id == user.id,
            PromoRedemption.status.in_(("applied", "used")),
            PromoRedemption.booking_id != booking.id,
        )
    )
    if (mine or 0) >= offer.per_user_limit:
        raise PromoErrors.used_up()
    if offer.first_booking_only:
        # "First booking" = no other booking has ever been paid for.
        paid_before = await db.scalar(
            select(func.count())
            .select_from(Payment)
            .join(Booking, Payment.booking_id == Booking.id)
            .where(
                Booking.user_id == user.id,
                Booking.id != booking.id,
                Payment.status.in_(("success", "refunded", "refund_pending")),
            )
        )
        if paid_before:
            raise PromoErrors.first_booking()


async def _no_open_payment(db: AsyncSession, booking: Booking) -> None:
    since = now() - _PAYMENT_SESSION
    open_ = await db.scalar(
        select(func.count()).where(Payment.booking_id == booking.id, Payment.status == "initiated", Payment.created_at >= since)
    )
    if open_:
        raise PromoErrors.payment_open()


def _set_discount(booking: Booking, discount: int, code: str | None) -> None:
    booking.total_amount = booking.total_amount + booking.discount - discount
    booking.discount = discount
    details = dict(booking.details or {})
    if code:
        details["promo_code"] = code
    else:
        details.pop("promo_code", None)
    booking.details = details


async def apply(db: AsyncSession, user: User, booking: Booking, code: str) -> Booking:
    if booking.status != "held":
        raise PromoErrors.not_held()
    await _no_open_payment(db, booking)
    t = today_bd()
    offer = await db.scalar(select(Offer).where(func.upper(Offer.code) == code.strip().upper()))
    if offer is None or not offer.is_active or not (offer.valid_from <= t <= offer.valid_to):
        raise PromoErrors.invalid()
    await _check(db, offer, user, booking)
    amount = discount_for(offer, _fare(booking))
    existing = await redemption_of(db, booking)
    if existing:
        existing.offer_id, existing.amount, existing.status = offer.id, amount, "applied"
    else:
        db.add(PromoRedemption(offer_id=offer.id, user_id=user.id, booking_id=booking.id, amount=amount, status="applied"))
    _set_discount(booking, amount, offer.code)
    await db.commit()
    await db.refresh(booking)
    return booking


async def remove(db: AsyncSession, booking: Booking) -> Booking:
    if booking.status != "held":
        raise PromoErrors.not_held()
    await _no_open_payment(db, booking)
    r = await redemption_of(db, booking)
    if r:
        await db.delete(r)
    _set_discount(booking, 0, None)
    await db.commit()
    await db.refresh(booking)
    return booking


async def check_method(db: AsyncSession, booking: Booking, method: str) -> None:
    """Some offers need a particular wallet (e.g. FLY8 with bKash)."""
    r = await redemption_of(db, booking)
    if r is None:
        return
    offer = await db.get(Offer, r.offer_id)
    if offer and offer.methods and method not in offer.methods:
        raise PromoErrors.method(offer.methods)


async def mark_used(db: AsyncSession, booking: Booking) -> None:
    r = await redemption_of(db, booking)
    if r and r.status == "applied":
        r.status = "used"
        offer = await db.get(Offer, r.offer_id)
        if offer:
            offer.used_count += 1


async def release(db: AsyncSession, booking_id: uuid.UUID) -> None:
    r = await db.scalar(select(PromoRedemption).where(PromoRedemption.booking_id == booking_id))
    if r and r.status == "applied":
        r.status = "released"
