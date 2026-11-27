from datetime import date, timedelta

from sqlalchemy import select, text, update

from app.db.session import SessionLocal
from app.models import Offer, PromoRedemption
from app.services.engagement import check_price_alerts
from tests.conftest import login
from tests.test_bus_booking import auth
from tests.test_payments import held_booking, pay_on_mock_page, start_payment
from tests.test_trips_refunds import inbox


async def promo(client, tokens, booking_id, code):
    return await client.post(f"/api/v1/bookings/{booking_id}/promo", json={"code": code}, headers=auth(tokens))


async def test_offers_list_and_mode_filter(client):
    r = await client.get("/api/v1/offers")
    codes = {o["code"] for o in r.json()["data"]}
    assert {"JATRI150", "FLY8", "RAIL50", "WELCOME100"} <= codes
    bus = {o["code"] for o in (await client.get("/api/v1/offers", params={"mode": "bus"})).json()["data"]}
    assert "JATRI150" in bus and "WELCOME100" in bus and "FLY8" not in bus


async def test_promo_lowers_the_price_and_is_used_on_payment(client, sms):
    tokens, _, _, b = await held_booking(client, sms)
    r = await promo(client, tokens, b["id"], "jatri150")  # case doesn't matter
    assert r.status_code == 200, r.text
    d = r.json()["data"]
    assert d["promo_code"] == "JATRI150" and d["discount"] == 15000
    assert d["total_amount"] == b["total_amount"] - 15000

    # Replacing the code: the new one wins, never both.
    r = await promo(client, tokens, b["id"], "WELCOME100")
    assert r.json()["data"]["discount"] == 10000 and r.json()["data"]["total_amount"] == b["total_amount"] - 10000
    r = await client.delete(f"/api/v1/bookings/{b['id']}/promo", headers=auth(tokens))
    assert r.json()["data"]["discount"] == 0 and r.json()["data"]["promo_code"] is None
    d = (await promo(client, tokens, b["id"], "JATRI150")).json()["data"]

    payment = await start_payment(client, tokens, b["id"])
    assert payment["amount"] == d["total_amount"]
    # While the gateway page is open the price can't change under it.
    r = await client.delete(f"/api/v1/bookings/{b['id']}/promo", headers=auth(tokens))
    assert r.status_code == 409 and r.json()["error"]["code"] == "payment_in_progress"
    await pay_on_mock_page(client, payment)
    async with SessionLocal() as db:
        assert (await db.scalar(select(PromoRedemption.status))) == "used"
        assert (await db.scalar(select(Offer.used_count).where(Offer.code == "JATRI150"))) == 1

    # First-booking codes and per-user limits apply to the next booking.
    _, _, _, b2 = await held_booking(client, sms)
    r = await promo(client, tokens, b2["id"], "JATRI150")
    assert r.status_code == 422 and r.json()["error"]["code"] in ("promo_used_up", "promo_first_booking")
    r = await promo(client, tokens, b2["id"], "WELCOME100")
    assert r.json()["error"]["code"] == "promo_first_booking"


async def test_promo_rules(client, sms):
    tokens, _, _, b = await held_booking(client, sms)
    assert (await promo(client, tokens, b["id"], "NOPE")).json()["error"]["code"] == "promo_invalid"
    assert (await promo(client, tokens, b["id"], "FLY8")).json()["error"]["code"] == "promo_wrong_mode"
    async with SessionLocal() as db:
        await db.execute(update(Offer).where(Offer.code == "WELCOME100").values(min_amount=10_000_000))
        await db.execute(update(Offer).where(Offer.code == "JATRI150").values(valid_to=date.today() - timedelta(days=2)))
        await db.commit()
    r = await promo(client, tokens, b["id"], "WELCOME100")
    assert r.json()["error"]["code"] == "promo_min_amount" and "১,০০,০০০" not in r.json()["error"]["message_en"]
    assert (await promo(client, tokens, b["id"], "JATRI150")).json()["error"]["code"] == "promo_invalid"


async def test_promo_payment_method_and_release_on_cancel(client, sms):
    tokens, _, _, b = await held_booking(client, sms)
    async with SessionLocal() as db:
        await db.execute(update(Offer).where(Offer.code == "WELCOME100").values(methods=["bkash"]))
        await db.commit()
    assert (await promo(client, tokens, b["id"], "WELCOME100")).status_code == 200
    r = await client.post("/api/v1/payments/init", json={"booking_id": b["id"], "method": "nagad"}, headers=auth(tokens))
    assert r.status_code == 409 and r.json()["error"]["code"] == "promo_method"
    assert "বিকাশ" in r.json()["error"]["message_bn"]
    # Cancelling the hold frees the code.
    await client.post(f"/api/v1/bookings/{b['id']}/cancel", headers=auth(tokens))
    async with SessionLocal() as db:
        assert (await db.scalar(select(PromoRedemption.status))) == "released"
    _, _, _, b2 = await held_booking(client, sms)
    assert (await promo(client, tokens, b2["id"], "WELCOME100")).status_code == 200


async def test_refund_after_promo_is_based_on_what_was_paid(client, sms):
    from tests.test_trips_refunds import depart_in, quote

    tokens, _, _, b = await held_booking(client, sms)
    d = (await promo(client, tokens, b["id"], "JATRI150")).json()["data"]
    await pay_on_mock_page(client, await start_payment(client, tokens, b["id"]))
    await depart_in(b["id"], 40)
    q = await quote(client, tokens, b["id"])
    assert q["paid"] == d["total_amount"] and q["paid"] == q["refund"] + q["fee"] + q["non_refundable"]


async def test_saved_travellers_crud_and_privacy(client, sms):
    tokens = (await login(client, sms))["tokens"]
    other = (await login(client, sms, "01812345678"))["tokens"]
    body = {
        "full_name": "রহিম উদ্দিন",
        "given_name": "Rahim",
        "surname": "Uddin",
        "gender": "male",
        "date_of_birth": "1990-05-01",
        "nid_number": "1990123456789",
        "passport_no": "A01234567",
        "passport_expiry": "2030-01-01",
    }
    r = await client.post("/api/v1/me/travellers", json=body, headers=auth(tokens))
    assert r.status_code == 200, r.text
    t = r.json()["data"]
    assert t["type"] == "adult" and t["nid_number"] == "1990123456789"
    async with SessionLocal() as db:  # encrypted at rest
        raw = (await db.execute(text("select nid_number from saved_travellers"))).scalar()
        assert raw and "1990123456789" not in raw

    kid = {"full_name": "Ayan Uddin", "gender": "male", "date_of_birth": (date.today() - timedelta(days=500)).isoformat()}
    assert (await client.post("/api/v1/me/travellers", json=kid, headers=auth(tokens))).json()["data"]["type"] == "infant"
    bad = body | {"passport_no": "A01234567", "passport_expiry": None}
    assert (await client.post("/api/v1/me/travellers", json=bad, headers=auth(tokens))).status_code == 422

    r = await client.put(f"/api/v1/me/travellers/{t['id']}", json=body | {"full_name": "Rahim  Uddin "}, headers=auth(tokens))
    assert r.json()["data"]["full_name"] == "Rahim Uddin"
    assert (await client.get("/api/v1/me/travellers", headers=auth(other))).json()["data"] == []
    assert (await client.delete(f"/api/v1/me/travellers/{t['id']}", headers=auth(other))).status_code == 404
    assert (await client.delete(f"/api/v1/me/travellers/{t['id']}", headers=auth(tokens))).status_code == 200
    assert len((await client.get("/api/v1/me/travellers", headers=auth(tokens))).json()["data"]) == 1


async def test_price_alert_fires_once_per_new_low(client, sms, settings):
    tokens = (await login(client, sms))["tokens"]
    day = date.today() + timedelta(days=15)
    body = {"mode": "flight", "from_id": "air_DAC", "to_id": "air_CXB", "date": day.isoformat(), "target_price": 100}
    r = await client.post("/api/v1/price-alerts", json=body, headers=auth(tokens))
    assert r.status_code == 200, r.text
    alert = r.json()["data"]
    now_price = alert["last_price"]
    assert now_price and now_price > 100

    flight = client.app.state.test_flight_provider
    async with SessionLocal() as db:
        assert await check_price_alerts(db, client.app.state.test_providers, settings) == 0  # still above the target
        await db.execute(update(type(await _alert(db))).values(target_price=now_price - 50_000))
        await db.commit()
        flight.price_bump_paisa = -100_000  # the airline drops its fares
        assert await check_price_alerts(db, client.app.state.test_providers, settings) == 1
        assert await check_price_alerts(db, client.app.state.test_providers, settings) == 0  # same low: no repeat
        flight.price_bump_paisa = -200_000
        assert await check_price_alerts(db, client.app.state.test_providers, settings) == 1  # a new low
    n = (await inbox(client, tokens))["items"][0]
    assert n["type"] == "price_drop" and n["data"]["mode"] == "flight" and "৳" in n["body_bn"]

    alerts = (await client.get("/api/v1/price-alerts", headers=auth(tokens))).json()["data"]
    assert alerts[0]["last_price"] < now_price
    assert (await client.delete(f"/api/v1/price-alerts/{alert['id']}", headers=auth(tokens))).status_code == 200


async def _alert(db):
    from app.models import PriceAlert

    return await db.scalar(select(PriceAlert))


async def test_price_alert_rules(client, sms, settings):
    tokens = (await login(client, sms))["tokens"]
    day = date.today() + timedelta(days=3)
    wrong = {"mode": "bus", "from_id": "air_DAC", "to_id": "air_CXB", "date": day.isoformat(), "target_price": 50000}
    assert (await client.post("/api/v1/price-alerts", json=wrong, headers=auth(tokens))).status_code == 422
    past = wrong | {"from_id": "bus_dhaka", "to_id": "bus_coxsbazar", "date": (date.today() - timedelta(days=1)).isoformat()}
    assert (await client.post("/api/v1/price-alerts", json=past, headers=auth(tokens))).json()["error"]["code"] == "date_passed"
    ok_body = past | {"date": day.isoformat()}
    r = await client.post("/api/v1/price-alerts", json=ok_body, headers=auth(tokens))
    assert r.json()["data"]["last_price"] >= 30000  # bus fares start at ৳300
    later = (date.today() + timedelta(days=20)).isoformat()
    train = {"mode": "train", "from_id": "stn_dhaka", "to_id": "stn_sylhet", "date": later, "target_price": 30000}
    r = await client.post("/api/v1/price-alerts", json=train, headers=auth(tokens))
    assert r.json()["data"]["last_price"] is None  # not on sale yet: checked again later
    for _ in range(8):
        await client.post("/api/v1/price-alerts", json=ok_body, headers=auth(tokens))
    r = await client.post("/api/v1/price-alerts", json=ok_body, headers=auth(tokens))
    assert r.json()["error"]["code"] == "too_many_alerts"
    async with SessionLocal() as db:
        from app.models import PriceAlert

        await db.execute(update(PriceAlert).values(date=date.today() - timedelta(days=1)))
        await db.commit()
        await check_price_alerts(db, client.app.state.test_providers, settings)
        assert all(not a.is_active for a in (await db.scalars(select(PriceAlert))).all())
