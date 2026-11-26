import uuid
from datetime import UTC, datetime, timedelta

import httpx
import pytest
from sqlalchemy import update

from app.core.config import Settings
from app.db.session import SessionLocal, engine
from app.models import Booking
from app.payments.base import CheckoutRequest, InvalidCallback
from app.payments.mock import MockGateway
from app.payments.sslcommerz import SslCommerzGateway
from app.services.booking import sweep_expired_holds
from app.services.ticket import verify_qr
from tests.conftest import login
from tests.test_bus_booking import auth, hold_body, seat_map, seats_with_state


async def held_booking(client, sms, phone="01712345678"):
    tokens = (await login(client, sms, phone))["tokens"]
    trip, body = await hold_body(client)
    r = await client.post("/api/v1/bookings", json=body, headers=auth(tokens))
    assert r.status_code == 200, r.text
    return tokens, trip, body, r.json()["data"]


async def start_payment(client, tokens, booking_id, method="bkash", key=None):
    headers = auth(tokens) | ({"Idempotency-Key": key} if key else {})
    r = await client.post("/api/v1/payments/init", json={"booking_id": booking_id, "method": method}, headers=headers)
    assert r.status_code == 200, r.text
    return r.json()["data"]


async def pay_on_mock_page(client, payment, result="success"):
    page = await client.get(payment["checkout_url"].replace("http://localhost:8000", ""))
    assert page.status_code == 200 and "SANDBOX" in page.text
    r = await client.post(f"/api/v1/payments/mock/complete/{payment['payment_id']}", data={"result": result})
    assert r.status_code == 303
    assert r.headers["location"] == f"jatri://payment/return?payment_id={payment['payment_id']}"


async def test_successful_payment_issues_ticket(client, sms, settings):
    tokens, trip, body, booking = await held_booking(client, sms)
    payment = await start_payment(client, tokens, booking["id"])
    assert payment["amount"] == booking["total_amount"]
    assert payment["status"] == "initiated"

    await pay_on_mock_page(client, payment)

    st = (await client.get(f"/api/v1/payments/{payment['payment_id']}/status", headers=auth(tokens))).json()["data"]
    assert st["status"] == "success"
    assert st["booking_status"] == "ticketed"

    b = (await client.get(f"/api/v1/bookings/{booking['id']}", headers=auth(tokens))).json()["data"]
    assert b["status"] == "ticketed" and b["hold_expires_at"] is None
    assert b["ticket"]["pdf_url"] == f"/api/v1/tickets/{booking['id']}/pdf"
    assert verify_qr(b["ticket"]["qr_payload"], settings)
    assert all(p["ticket_number"] for p in b["passengers"])

    # Sold seats are booked for everyone now.
    sm = await seat_map(client, trip["id"])
    assert set(body["seats"]) <= set(seats_with_state(sm, "booked"))

    pdf = await client.get(f"/api/v1/tickets/{booking['id']}/pdf", headers=auth(tokens))
    assert pdf.status_code == 200
    assert pdf.headers["content-type"] == "application/pdf"
    assert pdf.content.startswith(b"%PDF")


async def test_failed_payment_keeps_hold_and_allows_retry(client, sms):
    tokens, _, _, booking = await held_booking(client, sms)
    first = await start_payment(client, tokens, booking["id"])
    await pay_on_mock_page(client, first, result="fail")
    st = (await client.get(f"/api/v1/payments/{first['payment_id']}/status", headers=auth(tokens))).json()["data"]
    assert st["status"] == "failed" and st["booking_status"] == "held"

    second = await start_payment(client, tokens, booking["id"], method="nagad")
    assert second["payment_id"] != first["payment_id"]
    await pay_on_mock_page(client, second)
    st = (await client.get(f"/api/v1/payments/{second['payment_id']}/status", headers=auth(tokens))).json()["data"]
    assert st["booking_status"] == "ticketed"


async def test_init_is_idempotent(client, sms):
    tokens, _, _, booking = await held_booking(client, sms)
    a = await start_payment(client, tokens, booking["id"], key="pay-1")
    b = await start_payment(client, tokens, booking["id"], key="pay-1")
    assert a["payment_id"] == b["payment_id"]


async def test_replayed_callback_changes_nothing(client, sms, settings):
    tokens, _, _, booking = await held_booking(client, sms)
    payment = await start_payment(client, tokens, booking["id"])
    form = MockGateway(settings).completion_form(uuid.UUID(payment["payment_id"]), True, payment["amount"])
    r1 = await client.post("/api/v1/payments/callback/mock", data=form)
    r2 = await client.post("/api/v1/payments/callback/mock", data=form)
    assert r1.json()["data"]["booking_status"] == "ticketed"
    assert r2.json()["data"]["status"] == "success"  # no error, no second confirmation


async def test_forged_callback_is_rejected(client, sms, settings):
    tokens, _, _, booking = await held_booking(client, sms)
    payment = await start_payment(client, tokens, booking["id"])
    form = MockGateway(settings).completion_form(uuid.UUID(payment["payment_id"]), True, payment["amount"])
    form["signature"] = "0" * 64
    r = await client.post("/api/v1/payments/callback/mock", data=form)
    assert r.status_code == 400
    assert r.json()["error"]["code"] == "invalid_callback"
    st = (await client.get(f"/api/v1/payments/{payment['payment_id']}/status", headers=auth(tokens))).json()["data"]
    assert st["status"] == "initiated"


async def test_wrong_amount_is_not_accepted(client, sms, settings):
    tokens, _, _, booking = await held_booking(client, sms)
    payment = await start_payment(client, tokens, booking["id"])
    gw = MockGateway(settings)
    amount = str(payment["amount"] - 100)
    form = {"payment_id": payment["payment_id"], "status": "VALID", "amount": amount, "txn_id": "T1"}
    form["signature"] = gw.sign(form["payment_id"], "VALID", amount, "T1")
    r = await client.post("/api/v1/payments/callback/mock", data=form)
    assert r.json()["data"]["status"] == "failed"
    assert r.json()["data"]["failure_reason"] == "amount_mismatch"
    assert r.json()["data"]["booking_status"] == "held"


async def test_provider_refusal_after_payment_marks_refund(client, sms):
    tokens, _, _, booking = await held_booking(client, sms)
    payment = await start_payment(client, tokens, booking["id"])
    client.app.state.test_provider._holds.clear()  # the operator "lost" the hold
    await pay_on_mock_page(client, payment)
    st = (await client.get(f"/api/v1/payments/{payment['payment_id']}/status", headers=auth(tokens))).json()["data"]
    assert st["status"] == "refund_pending"
    assert st["booking_status"] == "failed"


async def test_expired_hold_cannot_be_paid(client, sms):
    tokens, _, _, booking = await held_booking(client, sms)
    async with engine.begin() as conn:
        await conn.execute(update(Booking).values(hold_expires_at=datetime.now(UTC) - timedelta(minutes=1)))
    r = await client.post("/api/v1/payments/init", json={"booking_id": booking["id"], "method": "bkash"}, headers=auth(tokens))
    assert r.status_code == 409
    assert r.json()["error"]["code"] == "booking_not_payable"


async def test_payments_and_tickets_are_private(client, sms):
    alice, _, _, booking = await held_booking(client, sms, "01712345678")
    bob = (await login(client, sms, "01812345678"))["tokens"]
    payment = await start_payment(client, alice, booking["id"])
    await pay_on_mock_page(client, payment)
    assert (await client.get(f"/api/v1/payments/{payment['payment_id']}/status", headers=auth(bob))).status_code == 404
    assert (await client.get(f"/api/v1/tickets/{booking['id']}/pdf", headers=auth(bob))).status_code == 404
    r = await client.post("/api/v1/payments/init", json={"booking_id": booking["id"], "method": "bkash"}, headers=auth(bob))
    assert r.status_code == 404


async def test_sweeper_releases_expired_holds(client, sms, store):
    tokens, _, body, booking = await held_booking(client, sms)
    async with engine.begin() as conn:
        await conn.execute(update(Booking).values(hold_expires_at=datetime.now(UTC) - timedelta(minutes=1)))
    async with SessionLocal() as db:
        assert await sweep_expired_holds(db, client.app.state.test_provider, store) == 1
    b = (await client.get(f"/api/v1/bookings/{booking['id']}", headers=auth(tokens))).json()["data"]
    assert b["status"] == "cancelled"


def test_qr_payload_cannot_be_forged(settings):
    from app.services.ticket import qr_payload

    class B:
        booking_ref = "JTW4US"
        id = uuid.uuid4()

    payload = qr_payload(B(), settings)
    assert verify_qr(payload, settings)
    assert not verify_qr(payload.replace("JTW4US", "JTW4UT"), settings)


# ---- SSLCommerz adapter against a fake SSLCommerz ------------------------------------------------


def _ssl_settings() -> Settings:
    return Settings(
        env="test",
        secret_key="x" * 40,
        payment_gateway="sslcommerz",
        sslcommerz_store_id="teststore",
        sslcommerz_store_password="testpass",
        public_base_url="https://api.example.com",
    )


async def test_sslcommerz_session_and_validation():
    seen = {}
    pid = uuid.uuid4()

    def handler(request: httpx.Request) -> httpx.Response:
        if request.url.path.endswith("/gwprocess/v4/api.php"):
            seen["session"] = dict(httpx.QueryParams(request.content.decode()))
            return httpx.Response(
                200, json={"status": "SUCCESS", "GatewayPageURL": "https://sandbox.sslcommerz.com/pay/abc", "sessionkey": "S1"}
            )
        if request.url.path.endswith("/validationserverAPI.php"):
            seen["validation"] = dict(request.url.params)
            return httpx.Response(
                200, json={"status": "VALID", "tran_id": str(pid), "amount": "1430.00", "currency": "BDT", "bank_tran_id": "BT1"}
            )
        return httpx.Response(404)

    gw = SslCommerzGateway(_ssl_settings(), client=httpx.AsyncClient(transport=httpx.MockTransport(handler)))
    session = await gw.create_session(
        CheckoutRequest(pid, 143000, "bkash", "JTW4US", "Rahim", "01712345678", None, "Bus ticket", 2)
    )
    assert session.checkout_url == "https://sandbox.sslcommerz.com/pay/abc"
    assert seen["session"]["total_amount"] == "1430.00"
    assert seen["session"]["tran_id"] == str(pid)
    assert seen["session"]["multi_card_name"] == "bkash"
    assert seen["session"]["ipn_url"] == "https://api.example.com/api/v1/payments/callback/sslcommerz"

    result = await gw.verify_callback({"tran_id": str(pid), "status": "VALID", "val_id": "V123"})
    assert result.success and result.amount_paisa == 143000 and result.gateway_txn_id == "BT1"
    assert seen["validation"]["val_id"] == "V123"

    failed = await gw.verify_callback({"tran_id": str(pid), "status": "FAILED"})
    assert not failed.success


async def test_sslcommerz_rejects_unvalidated_callback():
    pid = uuid.uuid4()

    def handler(request):
        return httpx.Response(200, json={"status": "INVALID_TRANSACTION"})

    gw = SslCommerzGateway(_ssl_settings(), client=httpx.AsyncClient(transport=httpx.MockTransport(handler)))
    with pytest.raises(InvalidCallback):
        await gw.verify_callback({"tran_id": str(pid), "status": "VALID", "val_id": "V1"})


def test_prod_refuses_mock_gateway():
    import app.core.config as cfg

    cfg.get_settings.cache_clear()
    import os

    old = dict(os.environ)
    os.environ.update(
        JATRI_ENV="prod", JATRI_SECRET_KEY="k" * 40, JATRI_DATA_KEY="x", JATRI_REDIS_URL="redis://r", JATRI_PAYMENT_GATEWAY="mock"
    )
    try:
        with pytest.raises(RuntimeError, match="mock payment gateway"):
            cfg.get_settings()
    finally:
        os.environ.clear()
        os.environ.update(old)
        cfg.get_settings.cache_clear()
