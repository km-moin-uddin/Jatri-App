"""Phone OTP login, token issuing, refresh rotation and logout."""

import hmac
import uuid
from dataclasses import dataclass
from datetime import timedelta

from sqlalchemy import func, select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import Settings
from app.core.envelope import Errors
from app.core.phone import normalize_bd_phone
from app.core.security import (
    create_access_token,
    generate_otp,
    hmac_hash,
    new_refresh_token,
    now,
)
from app.models import AuthOtp, RefreshToken, User
from app.services.sms import SmsSender, otp_message


@dataclass
class Tokens:
    access_token: str
    refresh_token: str
    expires_in: int


def require_phone(raw: str) -> str:
    phone = normalize_bd_phone(raw)
    if phone is None:
        raise Errors.invalid_phone()
    return phone


async def send_otp(db: AsyncSession, sms: SmsSender, settings: Settings, raw_phone: str) -> str:
    """Creates and sends a new OTP. Rate limited per phone: cooldown + max sends per window."""
    phone = require_phone(raw_phone)
    current = now()

    last_sent = await db.scalar(select(func.max(AuthOtp.created_at)).where(AuthOtp.phone == phone))
    if last_sent is not None:
        if last_sent.tzinfo is None:  # SQLite returns naive datetimes
            last_sent = last_sent.replace(tzinfo=current.tzinfo)
        wait = settings.otp_resend_cooldown_seconds - int((current - last_sent).total_seconds())
        if wait > 0:
            raise Errors.otp_cooldown(wait)

    window_start = current - timedelta(seconds=settings.otp_send_window_seconds)
    recent = await db.scalar(
        select(func.count()).select_from(AuthOtp).where(AuthOtp.phone == phone, AuthOtp.created_at >= window_start)
    )
    if (recent or 0) >= settings.otp_max_sends_per_window:
        raise Errors.otp_rate_limited()

    # Only the newest code is valid.
    await db.execute(update(AuthOtp).where(AuthOtp.phone == phone, AuthOtp.consumed.is_(False)).values(consumed=True))
    code = generate_otp(settings)
    db.add(
        AuthOtp(
            phone=phone,
            otp_hash=hmac_hash(f"{phone}:{code}", settings),
            expires_at=current + timedelta(seconds=settings.otp_ttl_seconds),
            created_at=current,
        )
    )
    await db.commit()
    await sms.send(phone, otp_message(code, settings))
    return phone


async def verify_otp(
    db: AsyncSession, settings: Settings, raw_phone: str, code: str, device_info: str | None
) -> tuple[User, Tokens, bool]:
    phone = require_phone(raw_phone)
    otp = await db.scalar(
        select(AuthOtp).where(AuthOtp.phone == phone, AuthOtp.consumed.is_(False)).order_by(AuthOtp.created_at.desc()).limit(1)
    )
    if otp is None:
        raise Errors.otp_expired()
    expires_at = otp.expires_at if otp.expires_at.tzinfo else otp.expires_at.replace(tzinfo=now().tzinfo)
    if expires_at < now():
        otp.consumed = True
        await db.commit()
        raise Errors.otp_expired()
    if otp.attempts >= settings.otp_max_attempts:
        raise Errors.otp_too_many_attempts()

    if not hmac.compare_digest(otp.otp_hash, hmac_hash(f"{phone}:{code}", settings)):
        otp.attempts += 1
        if otp.attempts >= settings.otp_max_attempts:
            otp.consumed = True
            await db.commit()
            raise Errors.otp_too_many_attempts()
        await db.commit()
        raise Errors.otp_invalid()

    otp.consumed = True
    user = await db.scalar(select(User).where(User.phone == phone))
    is_new = user is None
    if user is None:
        user = User(phone=phone)
        db.add(user)
        await db.flush()
    tokens = await _issue_tokens(db, settings, user.id, device_info)
    await db.commit()
    return user, tokens, is_new


async def refresh(db: AsyncSession, settings: Settings, raw_token: str) -> Tokens:
    """Rotates a refresh token. A revoked token being reused revokes the whole session family."""
    row = await db.scalar(select(RefreshToken).where(RefreshToken.token_hash == hmac_hash(raw_token, settings)))
    if row is None:
        raise Errors.invalid_refresh()
    if row.revoked:
        # Reuse of an old token: someone may have stolen it. Log this user out everywhere.
        await db.execute(update(RefreshToken).where(RefreshToken.user_id == row.user_id).values(revoked=True))
        await db.commit()
        raise Errors.invalid_refresh()
    expires_at = row.expires_at if row.expires_at.tzinfo else row.expires_at.replace(tzinfo=now().tzinfo)
    if expires_at < now():
        raise Errors.invalid_refresh()
    user = await db.get(User, row.user_id)
    if user is None or user.status != "active":
        raise Errors.invalid_refresh()
    row.revoked = True
    tokens = await _issue_tokens(db, settings, row.user_id, row.device_info)
    await db.commit()
    return tokens


async def logout(db: AsyncSession, settings: Settings, raw_token: str) -> None:
    await db.execute(update(RefreshToken).where(RefreshToken.token_hash == hmac_hash(raw_token, settings)).values(revoked=True))
    await db.commit()


async def _issue_tokens(db: AsyncSession, settings: Settings, user_id: uuid.UUID, device_info: str | None) -> Tokens:
    access, expires_in = create_access_token(user_id, settings)
    raw_refresh = new_refresh_token()
    db.add(
        RefreshToken(
            user_id=user_id,
            token_hash=hmac_hash(raw_refresh, settings),
            device_info=device_info,
            expires_at=now() + timedelta(days=settings.refresh_token_days),
        )
    )
    return Tokens(access, raw_refresh, expires_in)
