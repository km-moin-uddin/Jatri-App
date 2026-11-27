from fastapi import APIRouter

from app.api.deps import AppSettings, DbSession, Sms
from app.core.envelope import ok
from app.core.ratelimit import limit
from app.schemas.auth import (
    AuthOut,
    LogoutIn,
    OtpSendIn,
    OtpSendOut,
    OtpVerifyIn,
    RefreshIn,
    TokenPair,
)
from app.schemas.common import Envelope
from app.schemas.user import UserOut
from app.services import auth as service

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/otp/send", response_model=Envelope[OtpSendOut], dependencies=[limit("otp_send", 10, 600)])
async def send_otp(body: OtpSendIn, db: DbSession, sms: Sms, settings: AppSettings):
    phone = await service.send_otp(db, sms, settings, body.phone)
    return ok(OtpSendOut(phone=phone, expires_in=settings.otp_ttl_seconds, resend_after=settings.otp_resend_cooldown_seconds))


@router.post("/otp/verify", response_model=Envelope[AuthOut], dependencies=[limit("otp_verify", 30, 600)])
async def verify_otp(body: OtpVerifyIn, db: DbSession, settings: AppSettings):
    user, tokens, is_new = await service.verify_otp(db, settings, body.phone, body.code, body.device_info)
    return ok(
        AuthOut(
            tokens=TokenPair(access_token=tokens.access_token, refresh_token=tokens.refresh_token, expires_in=tokens.expires_in),
            user=UserOut.model_validate(user),
            is_new_user=is_new,
        )
    )


@router.post("/refresh", response_model=Envelope[TokenPair], dependencies=[limit("refresh", 30, 60)])
async def refresh(body: RefreshIn, db: DbSession, settings: AppSettings):
    tokens = await service.refresh(db, settings, body.refresh_token)
    return ok(TokenPair(access_token=tokens.access_token, refresh_token=tokens.refresh_token, expires_in=tokens.expires_in))


@router.post("/logout", response_model=Envelope[None])
async def logout(body: LogoutIn, db: DbSession, settings: AppSettings):
    await service.logout(db, settings, body.refresh_token)
    return ok(None)
