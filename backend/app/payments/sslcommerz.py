"""SSLCommerz adapter (bKash, Nagad, Rocket and cards through one gateway).

Flow, per SSLCommerz's integration guide:
1. Create a session (gwprocess/v4/api.php) and send the user to GatewayPageURL.
2. SSLCommerz POSTs the result to our IPN URL (server to server).
3. We confirm it with the Order Validation API using val_id before trusting it.
Sandbox first: set JATRI_SSLCOMMERZ_STORE_ID / _STORE_PASSWORD from a sandbox account.
"""

import uuid

import httpx

from app.core.config import Settings
from app.payments.base import CheckoutRequest, CheckoutSession, GatewayError, InvalidCallback, VerifiedCallback

_SANDBOX = "https://sandbox.sslcommerz.com"
_LIVE = "https://securepay.sslcommerz.com"
_METHOD = {"bkash": "bkash", "nagad": "nagad", "rocket": "dbbl_mobilebank", "card": "visacard,mastercard,amexcard"}


def _taka(paisa: int) -> str:
    return f"{paisa // 100}.{paisa % 100:02d}"


def _paisa(amount: str) -> int:
    taka, _, frac = amount.partition(".")
    return int(taka) * 100 + int((frac + "00")[:2])


class SslCommerzGateway:
    name = "sslcommerz"

    def __init__(self, settings: Settings, client: httpx.AsyncClient | None = None):
        if not settings.sslcommerz_store_id or not settings.sslcommerz_store_password:
            raise GatewayError("SSLCommerz store id/password are not configured")
        self._s = settings
        self._base = _SANDBOX if settings.sslcommerz_sandbox else _LIVE
        self._client = client or httpx.AsyncClient(timeout=20)

    async def create_session(self, req: CheckoutRequest) -> CheckoutSession:
        api = self._s.public_base_url.rstrip("/") + "/api/v1/payments"
        form = {
            "store_id": self._s.sslcommerz_store_id,
            "store_passwd": self._s.sslcommerz_store_password,
            "total_amount": _taka(req.amount_paisa),
            "currency": "BDT",
            "tran_id": str(req.payment_id),
            "success_url": f"{api}/return/sslcommerz",
            "fail_url": f"{api}/return/sslcommerz",
            "cancel_url": f"{api}/return/sslcommerz",
            "ipn_url": f"{api}/callback/sslcommerz",
            "multi_card_name": _METHOD.get(req.method, ""),
            "cus_name": req.customer_name or "Jatri traveller",
            "cus_phone": req.customer_phone,
            "cus_email": req.customer_email or "noreply@example.com",
            "cus_add1": "Bangladesh",
            "cus_city": "Dhaka",
            "cus_country": "Bangladesh",
            "shipping_method": "NO",
            "num_of_item": req.item_count,
            "product_name": req.product_name,
            "product_category": "travel",
            "product_profile": "airline-tickets",
            "value_a": req.booking_ref,
        }
        r = await self._client.post(f"{self._base}/gwprocess/v4/api.php", data=form)
        data = r.json()
        if data.get("status") != "SUCCESS" or not data.get("GatewayPageURL"):
            raise GatewayError(data.get("failedreason") or "session failed")
        return CheckoutSession(checkout_url=data["GatewayPageURL"], session_id=data.get("sessionkey"))

    async def verify_callback(self, form: dict) -> VerifiedCallback:
        try:
            payment_id = uuid.UUID(form["tran_id"])
        except (KeyError, ValueError) as e:
            raise InvalidCallback("bad tran_id") from e
        status = form.get("status", "")
        if status != "VALID":
            # Failed / cancelled: nothing to validate, the payment simply isn't paid.
            return VerifiedCallback(payment_id, False, None, None, raw={"status": status})
        val_id = form.get("val_id")
        if not val_id:
            raise InvalidCallback("missing val_id")
        r = await self._client.get(
            f"{self._base}/validator/api/validationserverAPI.php",
            params={
                "val_id": val_id,
                "store_id": self._s.sslcommerz_store_id,
                "store_passwd": self._s.sslcommerz_store_password,
                "format": "json",
            },
        )
        v = r.json()
        if v.get("status") not in ("VALID", "VALIDATED") or v.get("tran_id") != str(payment_id) or v.get("currency") != "BDT":
            raise InvalidCallback("validation failed")
        return VerifiedCallback(
            payment_id,
            True,
            _paisa(str(v.get("amount", "0"))),
            v.get("bank_tran_id") or val_id,
            raw={"status": v.get("status"), "card_type": v.get("card_type"), "val_id": val_id},
        )


def get_gateway(settings: Settings):
    if settings.payment_gateway == "sslcommerz":
        return SslCommerzGateway(settings)
    from app.payments.mock import MockGateway

    return MockGateway(settings)
