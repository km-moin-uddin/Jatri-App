"""SMS gateway adapter. Phase 2 ships a console sender; a Bangladeshi SMS provider plugs in later."""

import logging
from typing import Protocol

from app.core.config import Settings

log = logging.getLogger("jatri.sms")


class SmsSender(Protocol):
    async def send(self, phone: str, text: str) -> None: ...


class ConsoleSmsSender:
    """DEV ONLY: prints the SMS to the server log instead of sending it."""

    async def send(self, phone: str, text: str) -> None:
        log.warning("[DEV SMS] to %s: %s", phone, text)


def otp_message(code: str, settings: Settings) -> str:
    text = f"<#> Jatri code: {code}. Valid for {settings.otp_ttl_seconds // 60} minutes. Do not share it."
    if settings.android_app_hash:
        text += f"\n{settings.android_app_hash}"
    return text


def get_sms_sender(settings: Settings) -> SmsSender:
    if settings.sms_provider == "console":
        if settings.is_prod:
            raise RuntimeError("Configure a real SMS provider in production")
        return ConsoleSmsSender()
    raise RuntimeError(f"Unknown SMS provider: {settings.sms_provider}")
