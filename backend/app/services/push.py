"""Push messages (FCM HTTP v1). The console sender only logs, so dev and tests need no Firebase."""

import base64
import json
import logging
import time
from pathlib import Path
from typing import Protocol

import httpx
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding

from app.core.config import Settings

log = logging.getLogger("jatri.push")


class PushSender(Protocol):
    async def send(self, token: str, title: str, body: str, data: dict[str, str]) -> bool:
        """True if delivered to FCM; False means the token is dead and should be dropped."""
        ...


class ConsolePushSender:
    async def send(self, token: str, title: str, body: str, data: dict[str, str]) -> bool:
        log.info("[DEV PUSH] to %s…: %s — %s %s", token[:12], title, body, data)
        return True


def _b64(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")


class FcmPushSender:
    """FCM HTTP v1 with a service account: a signed JWT is exchanged for a 1-hour access token."""

    _SCOPE = "https://www.googleapis.com/auth/firebase.messaging"

    def __init__(self, project_id: str, service_account: str, client: httpx.AsyncClient | None = None):
        raw = Path(service_account).read_text() if not service_account.lstrip().startswith("{") else service_account
        self._sa = json.loads(raw)
        self._project = project_id
        self._client = client or httpx.AsyncClient(timeout=10)
        self._token: str | None = None
        self._expires = 0.0

    async def _access_token(self) -> str:
        if self._token and time.time() < self._expires - 60:
            return self._token
        now = int(time.time())
        header = _b64(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
        claims = _b64(
            json.dumps(
                {
                    "iss": self._sa["client_email"],
                    "scope": self._SCOPE,
                    "aud": self._sa["token_uri"],
                    "iat": now,
                    "exp": now + 3600,
                }
            ).encode()
        )
        key = serialization.load_pem_private_key(self._sa["private_key"].encode(), password=None)
        sig = key.sign(f"{header}.{claims}".encode(), padding.PKCS1v15(), hashes.SHA256())
        r = await self._client.post(
            self._sa["token_uri"],
            data={"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion": f"{header}.{claims}.{_b64(sig)}"},
        )
        r.raise_for_status()
        self._token = r.json()["access_token"]
        self._expires = time.time() + 3600
        return self._token

    async def send(self, token: str, title: str, body: str, data: dict[str, str]) -> bool:
        r = await self._client.post(
            f"https://fcm.googleapis.com/v1/projects/{self._project}/messages:send",
            headers={"Authorization": f"Bearer {await self._access_token()}"},
            json={"message": {"token": token, "notification": {"title": title, "body": body}, "data": data}},
        )
        if r.status_code in (400, 404) and ("UNREGISTERED" in r.text or "INVALID_ARGUMENT" in r.text):
            return False
        r.raise_for_status()
        return True


_fcm: FcmPushSender | None = None


def get_push_sender(settings: Settings) -> PushSender:
    global _fcm
    if settings.push_provider == "fcm":
        if not (settings.fcm_project_id and settings.fcm_service_account):
            raise RuntimeError("JATRI_FCM_PROJECT_ID and JATRI_FCM_SERVICE_ACCOUNT are needed for FCM")
        if _fcm is None:
            _fcm = FcmPushSender(settings.fcm_project_id, settings.fcm_service_account)
        return _fcm
    return ConsolePushSender()
