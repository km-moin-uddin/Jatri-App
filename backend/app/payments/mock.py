"""SANDBOX ONLY. A pretend gateway hosted by this API: a checkout page with Pay / Fail buttons.
Its "callback" is signed with HMAC exactly like a real gateway's, so the same verification and
booking-confirmation code runs. Refused when JATRI_ENV=prod."""

import hashlib
import hmac
import secrets
import uuid

from app.core.config import Settings
from app.payments.base import CheckoutRequest, CheckoutSession, InvalidCallback, RefundResult, VerifiedCallback


class MockGateway:
    name = "mock"

    def __init__(self, settings: Settings):
        self._settings = settings

    def sign(self, payment_id: str, status: str, amount: str, txn: str) -> str:
        msg = f"{payment_id}|{status}|{amount}|{txn}".encode()
        return hmac.new(("mockgw:" + self._settings.secret_key).encode(), msg, hashlib.sha256).hexdigest()

    async def create_session(self, req: CheckoutRequest) -> CheckoutSession:
        base = self._settings.public_base_url.rstrip("/")
        return CheckoutSession(checkout_url=f"{base}/api/v1/payments/mock/checkout/{req.payment_id}", session_id=None)

    def completion_form(self, payment_id: uuid.UUID, success: bool, amount_paisa: int) -> dict:
        status = "VALID" if success else "FAILED"
        txn = "MOCKTXN" + secrets.token_hex(5).upper() if success else ""
        amount = str(amount_paisa)
        return {
            "payment_id": str(payment_id),
            "status": status,
            "amount": amount,
            "txn_id": txn,
            "signature": self.sign(str(payment_id), status, amount, txn),
        }

    async def verify_callback(self, form: dict) -> VerifiedCallback:
        try:
            pid, status, amount, txn, sig = (form[k] for k in ("payment_id", "status", "amount", "txn_id", "signature"))
        except KeyError as e:
            raise InvalidCallback("missing field") from e
        if not hmac.compare_digest(sig, self.sign(pid, status, amount, txn)):
            raise InvalidCallback("bad signature")
        try:
            payment_id = uuid.UUID(pid)
        except ValueError as e:
            raise InvalidCallback("bad payment id") from e
        return VerifiedCallback(payment_id, status == "VALID", int(amount), txn or None, raw={"status": status, "txn_id": txn})

    async def refund(self, gateway_txn_id: str, amount_paisa: int, remarks: str) -> RefundResult:
        """Sandbox: the money "comes back" at once."""
        return RefundResult(done=True, ref="MOCKRFD" + secrets.token_hex(5).upper(), raw={"amount": amount_paisa})

    async def refund_status(self, ref: str) -> str:
        return "refunded"
