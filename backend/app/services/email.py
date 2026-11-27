"""E-mail with the PDF ticket. Console sender logs only; SMTP sends for real (in a thread)."""

import asyncio
import logging
import smtplib
from email.message import EmailMessage
from typing import Protocol

from app.core.config import Settings

log = logging.getLogger("jatri.email")


class EmailSender(Protocol):
    async def send(self, to: str, subject: str, text: str, attachment: tuple[str, bytes] | None = None) -> None: ...


class ConsoleEmailSender:
    async def send(self, to: str, subject: str, text: str, attachment: tuple[str, bytes] | None = None) -> None:
        log.info("[DEV EMAIL] to %s: %s (%s)", to, subject, attachment[0] if attachment else "no attachment")


class SmtpEmailSender:
    def __init__(self, settings: Settings):
        self._s = settings

    def _send(self, msg: EmailMessage) -> None:
        with smtplib.SMTP(self._s.smtp_host, self._s.smtp_port, timeout=20) as smtp:
            smtp.starttls()
            if self._s.smtp_user:
                smtp.login(self._s.smtp_user, self._s.smtp_password or "")
            smtp.send_message(msg)

    async def send(self, to: str, subject: str, text: str, attachment: tuple[str, bytes] | None = None) -> None:
        msg = EmailMessage()
        msg["From"], msg["To"], msg["Subject"] = self._s.email_from, to, subject
        msg.set_content(text)
        if attachment:
            msg.add_attachment(attachment[1], maintype="application", subtype="pdf", filename=attachment[0])
        await asyncio.to_thread(self._send, msg)


def get_email_sender(settings: Settings) -> EmailSender:
    if settings.email_provider == "smtp":
        if not settings.smtp_host:
            raise RuntimeError("JATRI_SMTP_HOST is needed for e-mail")
        return SmtpEmailSender(settings)
    return ConsoleEmailSender()
