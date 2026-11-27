from datetime import UTC, datetime, timedelta

import httpx
from sqlalchemy import select, update

from app.db.session import SessionLocal
from app.models import Booking, BookingSegment, DeviceToken, Payment, Refund
from app.payments.mock import MockGateway
from app.payments.sslcommerz import SslCommerzGateway
from app.services import notify as notify_module
from app.services.cancellation import process_refunds
from app.services.journeys import check_flight_changes, send_journey_reminders
from tests.conftest import login
from tests.test_bus_booking import auth
from tests.test_flight_booking import adult, book, search
from tests.test_payments import _ssl_settings, held_booking, pay_on_mock_page, start_payment


async def ticketed_bus(client, sms, phone="01712345678"):
    tokens, trip, body, booking = await held_booking(client, sms, phone)
    await pay_on_mock_page(client, await start_payment(client, tokens, booking["id"]))
    return tokens, booking


async def depart_in(booking_id: str, hours: float):
    """Move every leg so the trip starts `hours` from now (keeps leg gaps)."""
    import uuid

    async with SessionLocal() as db:
        segs = (await db.scalars(select(BookingSegment).where(BookingSegment.booking_id == uuid.UUID(booking_id)))).all()
        first = min((s.depart_at if s.depart_at.tzinfo else s.depart_at.replace(tzinfo=UTC)) for s in segs)
        shift = datetime.now(UTC) + timedelta(hours=hours) - first
        for s in segs:
            s.depart_at = (s.depart_at if s.depart_at.tzinfo else s.depart_at.replace(tzinfo=UTC)) + shift
            s.arrive_at = (s.arrive_at if s.arrive_at.tzinfo else s.arrive_at.replace(tzinfo=UTC)) + shift
        await db.commit()


async def quote(client, tokens, booking_id):
    r = await client.get(f"/api/v1/bookings/{booking_id}/cancel-quote", headers=auth(tokens))
    assert r.status_code == 200, r.text
    return r.json()["data"]


async def inbox(client, tokens):
    r = await client.get("/api/v1/notifications", headers=auth(tokens))
    assert r.status_code == 200, r.text
    return r.json()["data"]


async def test_bus_quote_follows_the_policy(client, sms):
    tokens, b = await ticketed_bus(client, sms)
    paid, service = b["total_amount"], b["service_charge"]

    await depart_in(b["id"], 30)
    q = await quote(client, tokens, b["id"])
    assert q["cancellable"] and q["paid"] == paid and q["non_refundable"] == service
    assert q["fee"] == round((paid - service) * 0.10 / 100) * 100
    assert q["refund"] == paid - service - q["fee"]

    await depart_in(b["id"], 15)
    assert (await quote(client, tokens, b["id"]))["fee"] == round((paid - service) * 0.50 / 100) * 100

    await depart_in(b["id"], 5)
    q = await quote(client, tokens, b["id"])
    assert not q["cancellable"] and q["reason"] == "too_late" and q["refund"] == 0
    r = await client.post(f"/api/v1/bookings/{b['id']}/cancel", headers=auth(tokens))
    assert r.status_code == 409 and r.json()["error"]["code"] == "too_late"


async def test_cancel_ticket_refunds_and_notifies(client, sms):
    tokens, b = await ticketed_bus(client, sms)
    await depart_in(b["id"], 40)
    q = await quote(client, tokens, b["id"])

    r = await client.post(f"/api/v1/bookings/{b['id']}/cancel", json={"expected_refund": q["refund"] + 100}, headers=auth(tokens))
    assert r.status_code == 409 and r.json()["error"]["code"] == "refund_changed"

    r = await client.post(f"/api/v1/bookings/{b['id']}/cancel", json={"expected_refund": q["refund"]}, headers=auth(tokens))
    assert r.status_code == 200, r.text
    done = r.json()["data"]
    # The mock gateway returns the money at once, so the booking is already refunded.
    assert done["status"] == "refunded" and done["cancelled_at"]
    assert (
        done["refund"]["amount"] == q["refund"] and done["refund"]["fee"] == q["fee"] and done["refund"]["status"] == "refunded"
    )
    async with SessionLocal() as db:
        assert (await db.scalar(select(Payment.status).where(Payment.booking_id == Booking.id))) == "refunded"

    box = await inbox(client, tokens)
    assert [n["type"] for n in box["items"]] == ["refund_processed", "booking_cancelled", "booking_confirmed"]
    assert box["unread"] == 3
    assert "৳" in box["items"][0]["body_bn"] and box["items"][0]["data"]["booking_id"] == b["id"]

    # Cancelling twice changes nothing.
    again = await client.post(f"/api/v1/bookings/{b['id']}/cancel", headers=auth(tokens))
    assert again.status_code == 200 and again.json()["data"]["status"] == "refunded"


async def test_flight_quotes_follow_fare_rules(client, sms):
    from tests.test_flight_booking import DAY

    tokens = (await login(client, sms))["tokens"]
    offers = await search(client, [("air_DAC", "air_CXB", DAY)])

    async def ticketed(offer, key):
        b = (await book(client, tokens, offer, [adult()], key=key)).json()["data"]
        await pay_on_mock_page(client, await start_payment(client, tokens, b["id"]))
        return b

    flex = await ticketed(next(o for o in offers if o["fare_brand"] == "flex"), "f1")
    q = await quote(client, tokens, flex["id"])
    assert q["cancellable"] and q["fee"] == 150000 and q["refund"] == flex["total_amount"] - flex["service_charge"] - 150000

    saver = await ticketed(next(o for o in offers if o["fare_brand"] == "saver"), "f2")
    q = await quote(client, tokens, saver["id"])
    assert q["cancellable"] and q["refund"] == 0 and q["reason"] == "non_refundable"
    r = await client.post(f"/api/v1/bookings/{saver['id']}/cancel", json={"expected_refund": 0}, headers=auth(tokens))
    assert r.json()["data"]["status"] == "cancelled" and r.json()["data"]["refund"] is None

    await depart_in(flex["id"], 2)  # inside the 3-hour deadline
    q = await quote(client, tokens, flex["id"])
    assert not q["cancellable"] and q["reason"] == "too_late"


async def test_held_booking_cancel_still_releases(client, sms):
    tokens, _, _, b = await held_booking(client, sms)
    q = await quote(client, tokens, b["id"])
    assert q["cancellable"] and q["paid"] == 0
    r = await client.post(f"/api/v1/bookings/{b['id']}/cancel", headers=auth(tokens))
    assert r.json()["data"]["status"] == "cancelled"


async def test_provider_refusal_is_refunded_in_full(client, sms, settings):
    tokens, _, _, b = await held_booking(client, sms)
    client.app.state.test_provider._holds.clear()  # the operator "lost" the hold
    await pay_on_mock_page(client, await start_payment(client, tokens, b["id"]))
    assert (await client.get(f"/api/v1/bookings/{b['id']}", headers=auth(tokens))).json()["data"]["status"] == "failed"
    async with SessionLocal() as db:
        assert await process_refunds(db, MockGateway(settings), settings) == 1
        refund = await db.scalar(select(Refund))
        assert refund.amount == b["total_amount"] and refund.reason == "provider_refused"
    got = (await client.get(f"/api/v1/bookings/{b['id']}", headers=auth(tokens))).json()["data"]
    assert got["status"] == "refunded"
    assert [n["type"] for n in (await inbox(client, tokens))["items"]][:2] == ["refund_processed", "booking_failed"]


async def test_notifications_read_and_privacy(client, sms):
    tokens, b = await ticketed_bus(client, sms)
    other = (await login(client, sms, "01812345678"))["tokens"]
    box = await inbox(client, tokens)
    nid = box["items"][0]["id"]
    assert box["unread"] == 1
    assert (await client.post(f"/api/v1/notifications/{nid}/read", headers=auth(other))).status_code == 404
    r = await client.post(f"/api/v1/notifications/{nid}/read", headers=auth(tokens))
    assert r.json()["data"]["is_read"] is True
    assert (await inbox(client, tokens))["unread"] == 0
    assert (await inbox(client, other))["items"] == []
    await client.post("/api/v1/notifications/read-all", headers=auth(tokens))


async def test_push_goes_to_registered_devices_and_drops_dead_ones(client, sms, monkeypatch):
    sent = []

    class FakePush:
        async def send(self, token, title, body, data):
            sent.append((token, title, data["type"]))
            return token != "dead-token-0123456789-xxxx"

    monkeypatch.setattr(notify_module, "get_push_sender", lambda settings: FakePush())
    tokens = (await login(client, sms))["tokens"]
    for t in ("good-token-0123456789-xxxx", "dead-token-0123456789-xxxx"):
        assert (await client.post("/api/v1/devices", json={"token": t}, headers=auth(tokens))).status_code == 200
    tokens, b = await ticketed_bus(client, sms)
    assert {s[0] for s in sent} == {"good-token-0123456789-xxxx", "dead-token-0123456789-xxxx"}
    assert sent[0][1] == "টিকিট কনফার্ম হয়েছে 🎉"  # the user's language (Bangla by default)
    async with SessionLocal() as db:
        assert [t.token for t in (await db.scalars(select(DeviceToken))).all()] == ["good-token-0123456789-xxxx"]
    r = await client.delete("/api/v1/devices/good-token-0123456789-xxxx", headers=auth(tokens))
    assert r.status_code == 200


async def test_ticket_is_sent_by_sms_and_email(client, sms, monkeypatch):
    import app.services.email as email_module
    import app.services.sms as sms_module

    mails, texts = [], []

    class FakeMail:
        async def send(self, to, subject, text, attachment=None):
            mails.append((to, subject, attachment))

    class FakeSms:
        async def send(self, phone, text):
            texts.append((phone, text))

    monkeypatch.setattr(email_module, "get_email_sender", lambda s: FakeMail())
    monkeypatch.setattr(sms_module, "get_sms_sender", lambda s: FakeSms())
    tokens = (await login(client, sms))["tokens"]
    from tests.test_bus_booking import hold_body

    _, body = await hold_body(client)
    body["contact_email"] = "rahim@example.com"
    b = (await client.post("/api/v1/bookings", json=body, headers=auth(tokens))).json()["data"]
    await pay_on_mock_page(client, await start_payment(client, tokens, b["id"]))
    assert texts and b["booking_ref"] in texts[0][1]
    assert mails[0][0] == "rahim@example.com" and mails[0][2][1].startswith(b"%PDF")


async def test_journey_reminders_are_sent_once(client, sms, settings):
    tokens, b = await ticketed_bus(client, sms)
    await depart_in(b["id"], 20)
    async with SessionLocal() as db:
        assert await send_journey_reminders(db, settings) == 1
        assert await send_journey_reminders(db, settings) == 0
    await depart_in(b["id"], 2)
    async with SessionLocal() as db:
        assert await send_journey_reminders(db, settings) == 1
        assert await send_journey_reminders(db, settings) == 0
    types = [(n["type"], n["title_en"]) for n in (await inbox(client, tokens))["items"]]
    assert ("journey_reminder", "Your trip is in 3 hours") in types and ("journey_reminder", "Your trip is in 24 hours") in types


async def test_flight_delay_moves_times_and_alerts(client, sms, settings):
    from tests.test_flight_booking import DAY

    tokens = (await login(client, sms))["tokens"]
    offer = (await search(client, [("air_DAC", "air_CXB", DAY)]))[0]
    b = (await book(client, tokens, offer, [adult()])).json()["data"]
    await pay_on_mock_page(client, await start_payment(client, tokens, b["id"]))
    before = (await client.get(f"/api/v1/bookings/{b['id']}", headers=auth(tokens))).json()["data"]
    pnr = next(iter(client.app.state.test_flight_provider._holds))
    client.app.state.test_flight_provider.delays[pnr] = 45
    async with SessionLocal() as db:
        assert await check_flight_changes(db, client.app.state.test_providers, settings) == 1
        assert await check_flight_changes(db, client.app.state.test_providers, settings) == 0
    after = (await client.get(f"/api/v1/bookings/{b['id']}", headers=auth(tokens))).json()["data"]
    old = datetime.fromisoformat(before["segments"][0]["depart_at"])
    new = datetime.fromisoformat(after["segments"][0]["depart_at"])
    assert new - old == timedelta(minutes=45)
    assert (await inbox(client, tokens))["items"][0]["type"] == "flight_changed"


async def test_sslcommerz_refund_api():
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append(dict(request.url.params))
        if "refund_ref_id" in request.url.params:
            return httpx.Response(200, json={"APIConnect": "DONE", "status": "refunded"})
        return httpx.Response(200, json={"APIConnect": "DONE", "status": "success", "refund_ref_id": "RF1"})

    gw = SslCommerzGateway(_ssl_settings(), client=httpx.AsyncClient(transport=httpx.MockTransport(handler)))
    result = await gw.refund("BT1", 125050, "Jatri refund user_cancel")
    assert not result.done and result.ref == "RF1"
    assert calls[0]["refund_amount"] == "1250.50" and calls[0]["bank_tran_id"] == "BT1"
    assert await gw.refund_status("RF1") == "refunded"


async def test_failed_refund_is_retried_then_flagged(client, sms, settings):
    from app.payments.base import GatewayError

    class Broken:
        async def refund(self, *a):
            raise GatewayError("down")

        async def refund_status(self, ref):
            return "processing"

    tokens, b = await ticketed_bus(client, sms)
    await depart_in(b["id"], 40)
    import app.api.v1.payments as payments_api

    client.app.dependency_overrides[payments_api.gateway_dep] = lambda: Broken()
    r = await client.post(f"/api/v1/bookings/{b['id']}/cancel", headers=auth(tokens))
    assert r.status_code == 200 and r.json()["data"]["status"] == "cancelled"
    assert r.json()["data"]["refund"]["status"] == "pending"
    async with SessionLocal() as db:
        for _ in range(5):
            await process_refunds(db, Broken(), settings)
        assert (await db.scalar(select(Refund.status))) == "failed"
        await db.execute(update(Refund).values(status="pending", attempts=0))
        await db.commit()
        assert await process_refunds(db, MockGateway(settings), settings) == 1
