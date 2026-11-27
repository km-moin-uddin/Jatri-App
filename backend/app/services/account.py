"""Account deletion (Google Play requirement): personal data goes, accounting records stay anonymous."""

from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models import (
    AuthOtp,
    Booking,
    DeviceToken,
    Notification,
    PriceAlert,
    PromoRedemption,
    RefreshToken,
    SavedTraveller,
    User,
)
from app.providers.registry import Providers
from app.services.booking import _release_hold
from app.services.cache import KeyValueStore

DELETED_NAME = "Deleted user"


async def delete_account(db: AsyncSession, providers: Providers, store: KeyValueStore, user: User) -> None:
    """
    - Unpaid holds are released (seats and promo codes go back).
    - Every booking loses its link to the account and its contact details; passengers lose names,
      dates of birth and NID / passport numbers. Amounts, references, ticket numbers and status stay
      for accounting, refunds and disputes.
    - Sessions, push tokens, login codes, saved travellers, price alerts, notifications and promo
      redemptions are deleted (explicitly, not trusting ON DELETE CASCADE on every database).
    """
    bookings = (await db.scalars(select(Booking).where(Booking.user_id == user.id))).all()
    for b in bookings:
        if b.status == "held":
            await _release_hold(providers, store, b, db)
            b.status = "cancelled"
        b.user_id = None
        b.contact_phone = ""
        b.contact_email = None
        for p in b.passengers:
            p.full_name = DELETED_NAME
            p.document_no = None
            p.date_of_birth = None
            p.document_expiry = None
    for model in (RefreshToken, DeviceToken, SavedTraveller, PriceAlert, Notification, PromoRedemption):
        await db.execute(delete(model).where(model.user_id == user.id))
    await db.execute(delete(AuthOtp).where(AuthOtp.phone == user.phone))
    await db.flush()
    await db.delete(user)
    await db.commit()
