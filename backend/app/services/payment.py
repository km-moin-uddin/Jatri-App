"""Payments (spec section 10, steps 4-6):
init → user pays at the gateway → server-to-server callback → verify signature/validation AND
amount → confirm with the provider → issue ticket → `ticketed`.
If the provider refuses after money was taken: booking `failed`, payment `refund_pending`, alert."""

import logging
import uuid

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import Settings
from app.core.envelope import AppError
from app.core.security import now
from app.models import Booking, Payment, Ticket, User
from app.payments.base import CheckoutRequest, InvalidCallback
from app.providers.base import ProviderError
from app.providers.mock_bus import MockBusProvider
from app.services.booking import BookingErrors, expire_if_needed, get_owned
from app.services.cache import KeyValueStore, seat_key
from app.services.ticket import qr_payload

log = logging.getLogger("jatri.payments")

METHODS = ("bkash", "nagad", "rocket", "card")


class PaymentErrors:
    @staticmethod
    def not_payable() -> AppError:
        return AppError(
            409,
            "booking_not_payable",
            "This booking can no longer be paid. Please choose seats again.",
            "এই বুকিংয়ের পেমেন্ট আর করা যাবে না। আবার সিট বেছে নিন।",
        )

    @staticmethod
    def gateway_unavailable() -> AppError:
        return AppError(
            503,
            "gateway_unavailable",
            "The payment service is not responding. Please try again.",
            "পেমেন্ট সার্ভিস সাড়া দিচ্ছে না। আবার চেষ্টা করুন।",
        )

    @staticmethod
    def not_found() -> AppError:
        return AppError(404, "payment_not_found", "Payment not found.", "পেমেন্ট পাওয়া যায়নি।")

    @staticmethod
    def invalid_callback() -> AppError:
        return AppError(400, "invalid_callback", "Invalid callback.", "অবৈধ কলব্যাক।")


async def init_payment(
    db: AsyncSession,
    gateway,
    provider: MockBusProvider,
    store: KeyValueStore,
    user: User,
    booking_id: uuid.UUID,
    method: str,
    idempotency_key: str | None,
) -> tuple[Payment, str]:
    booking = await get_owned(db, provider, store, user, booking_id)
    if booking.status != "held":
        raise PaymentErrors.not_payable()

    if idempotency_key:
        existing = await db.scalar(
            select(Payment).where(Payment.booking_id == booking.id, Payment.idempotency_key == idempotency_key)
        )
        if existing is not None and existing.raw_response.get("checkout_url"):
            return existing, existing.raw_response["checkout_url"]

    payment = Payment(
        id=uuid.uuid4(),
        booking_id=booking.id,
        gateway=gateway.name,
        method=method,
        amount=booking.total_amount,
        status="initiated",
        raw_response={},
        idempotency_key=idempotency_key,
    )
    seg = booking.segments[0]
    try:
        session = await gateway.create_session(
            CheckoutRequest(
                payment_id=payment.id,
                amount_paisa=booking.total_amount,
                method=method,
                booking_ref=booking.booking_ref,
                customer_name=user.name or "",
                customer_phone=booking.contact_phone,
                customer_email=booking.contact_email or user.email,
                product_name=f"Bus ticket {seg.carrier} {booking.booking_ref}",
                item_count=len(seg.seat_numbers),
            )
        )
    except Exception as e:  # network / gateway refused
        log.warning("gateway session failed for booking %s: %s", booking.booking_ref, type(e).__name__)
        raise PaymentErrors.gateway_unavailable() from e
    payment.gateway_session = session.session_id
    payment.raw_response = {"checkout_url": session.checkout_url}
    db.add(payment)
    await db.commit()
    return payment, session.checkout_url


async def process_callback(
    db: AsyncSession, gateway, provider: MockBusProvider, store: KeyValueStore, settings: Settings, form: dict
) -> Payment:
    """Idempotent: a callback for a payment that already left `initiated` changes nothing."""
    try:
        result = await gateway.verify_callback(form)
    except InvalidCallback as e:
        log.warning("rejected %s callback: %s", gateway.name, e)
        raise PaymentErrors.invalid_callback() from e

    stmt = select(Payment).where(Payment.id == result.payment_id)
    if db.bind.dialect.name == "postgresql":
        stmt = stmt.with_for_update()  # two callbacks at once must not both confirm
    payment = await db.scalar(stmt)
    if payment is None or payment.gateway != gateway.name:
        raise PaymentErrors.not_found()
    if payment.status != "initiated":
        return payment

    payment.raw_response = {**payment.raw_response, "callback": result.raw}
    payment.gateway_txn_id = result.gateway_txn_id
    if not result.success:
        payment.status = "failed"
        payment.failure_reason = "declined"
        await db.commit()
        return payment

    if result.amount_paisa != payment.amount:
        payment.status = "failed"
        payment.failure_reason = "amount_mismatch"
        await db.commit()
        log.error("ALERT amount mismatch on payment %s: got %s expected %s", payment.id, result.amount_paisa, payment.amount)
        return payment

    payment.status = "success"
    booking = await db.get(Booking, payment.booking_id)
    await _confirm_and_ticket(db, provider, store, settings, booking, payment)
    await db.commit()
    return payment


async def _confirm_and_ticket(
    db: AsyncSession, provider: MockBusProvider, store: KeyValueStore, settings: Settings, booking: Booking, payment: Payment
) -> None:
    if booking.status not in ("held", "cancelled"):
        return  # already confirmed by an earlier payment
    try:
        # Even if the hold just expired, try: the money is real, so get the seats if still possible.
        pnr = await provider.confirm(booking.provider_booking_ref or "")
    except ProviderError as e:
        booking.status = "failed"
        payment.status = "refund_pending"
        payment.failure_reason = e.code
        log.error("ALERT provider refused paid booking %s (%s): refund needed", booking.booking_ref, e.code)
        return
    booking.status = "ticketed"
    booking.hold_expires_at = None
    booking.provider_booking_ref = pnr
    for i, p in enumerate(booking.passengers):
        p.ticket_number = f"{pnr}-{i + 1}"
    db.add(
        Ticket(
            booking_id=booking.id,
            qr_payload=qr_payload(booking, settings),
            pdf_url=f"/api/v1/tickets/{booking.id}/pdf",
            issued_at=now(),
        )
    )
    # Seats are sold now; the provider shows them as booked, so drop our temporary locks.
    for seg in booking.segments:
        await store.release([seat_key(seg.trip_id, n) for n in seg.seat_numbers], str(booking.id))


async def payment_status(db: AsyncSession, provider, store, user: User, payment_id: uuid.UUID) -> tuple[Payment, Booking]:
    payment = await db.get(Payment, payment_id)
    if payment is None:
        raise PaymentErrors.not_found()
    booking = await db.get(Booking, payment.booking_id)
    if booking is None or booking.user_id != user.id:
        raise PaymentErrors.not_found()
    booking = await expire_if_needed(db, provider, store, booking)
    return payment, booking


async def ticket_for(db: AsyncSession, provider, store, user: User, booking_id: uuid.UUID) -> tuple[Booking, Ticket]:
    booking = await get_owned(db, provider, store, user, booking_id)
    ticket = await db.scalar(select(Ticket).where(Ticket.booking_id == booking.id))
    if ticket is None:
        raise BookingErrors.not_found()
    return booking, ticket
