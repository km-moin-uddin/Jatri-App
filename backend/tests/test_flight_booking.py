from datetime import UTC, date, datetime, timedelta

from app.services.ticket import verify_qr
from tests.conftest import login
from tests.test_bus_booking import auth
from tests.test_payments import pay_on_mock_page, start_payment

DAY = date.today() + timedelta(days=20)


async def search(client, slices, adults=1, children=0, infants=0, cabin="economy"):
    body = {
        "slices": [{"from_id": a, "to_id": b, "date": d.isoformat()} for a, b, d in slices],
        "adults": adults,
        "children": children,
        "infants": infants,
        "cabin": cabin,
    }
    r = await client.post("/api/v1/flights/search", json=body)
    assert r.status_code == 200, r.text
    return r.json()["data"]


def adult(given="Rahim", surname="Uddin", **kw):
    return {"type": "adult", "given_name": given, "surname": surname, "gender": "male", "date_of_birth": "1990-05-01", **kw}


def passport(**kw):
    return {"document_no": "A01234567", "document_expiry": (DAY + timedelta(days=400)).isoformat(), **kw}


async def book(client, tokens, offer, passengers, key=None, expected=None):
    body = {
        "mode": "flight",
        "offer_id": offer["id"],
        "passengers": passengers,
        "contact_phone": "01712345678",
        "expected_total": offer["total"] if expected is None else expected,
    }
    headers = auth(tokens) | ({"Idempotency-Key": key} if key else {})
    return await client.post("/api/v1/bookings", json=body, headers=headers)


async def test_one_way_search_returns_normalised_offers(client):
    offers = await search(client, [("air_DAC", "air_CXB", DAY)], adults=2, children=1, infants=1)
    assert offers
    o = offers[0]
    assert len(o["slices"]) == 1 and o["slices"][0]["stops"] == 0
    leg = o["slices"][0]["legs"][0]
    assert leg["from_code"] == "DAC" and leg["to_code"] == "CXB"
    assert leg["depart_at"].endswith("+06:00")
    assert {f["type"] for f in o["fares"]} == {"adult", "child", "infant"}
    assert o["total"] == o["base_total"] + o["tax_total"] + o["service_charge"]
    assert o["service_charge"] == 3 * 20000  # infants don't pay the service charge
    assert not o["international"]
    # Both a cheap non-refundable fare and a refundable one are offered.
    assert {x["fare_brand"] for x in offers} == {"saver", "flex"}
    assert any(not x["rules"]["refundable"] for x in offers) and any(x["rules"]["refundable"] for x in offers)


async def test_round_trip_multi_city_and_connections(client):
    rt = await search(client, [("air_DAC", "air_DXB", DAY), ("air_DXB", "air_DAC", DAY + timedelta(days=7))])
    assert rt and all(len(o["slices"]) == 2 and o["international"] for o in rt)
    assert rt[0]["slices"][1]["legs"][0]["depart_at"].endswith("+04:00")  # Dubai local time

    mc = await search(
        client,
        [
            ("air_DAC", "air_CXB", DAY),
            ("air_CXB", "air_CGP", DAY + timedelta(days=2)),
            ("air_CGP", "air_DAC", DAY + timedelta(days=4)),
        ],
    )
    assert mc and all(len(o["slices"]) == 3 for o in mc)

    # No direct flight between two regional airports: connect through Dhaka.
    via = await search(client, [("air_JSR", "air_ZYL", DAY)])
    assert via and all(o["slices"][0]["stops"] == 1 and o["slices"][0]["legs"][0]["to_code"] == "DAC" for o in via)


async def test_search_validation(client):
    r = await client.post(
        "/api/v1/flights/search",
        json={"slices": [{"from_id": "air_DAC", "to_id": "air_CXB", "date": DAY.isoformat()}], "adults": 1, "infants": 2},
    )
    assert r.status_code == 422  # more infants than adults
    r = await client.post(
        "/api/v1/flights/search",
        json={
            "slices": [
                {"from_id": "air_DAC", "to_id": "air_CXB", "date": DAY.isoformat()},
                {"from_id": "air_CXB", "to_id": "air_DAC", "date": (DAY - timedelta(days=1)).isoformat()},
            ]
        },
    )
    assert r.status_code == 422  # return before outbound


async def test_offer_details_and_expiry(client):
    o = (await search(client, [("air_DAC", "air_ZYL", DAY)]))[0]
    r = await client.get(f"/api/v1/flights/{o['id']}")
    assert r.status_code == 200 and r.json()["data"]["total"] == o["total"]
    assert r.json()["data"]["rules"]["checked_baggage_kg"] == o["rules"]["checked_baggage_kg"]
    gone = o["id"].replace(DAY.strftime("%Y%m%d"), (date.today() - timedelta(days=1)).strftime("%Y%m%d"))
    r = await client.get(f"/api/v1/flights/{gone}")
    assert r.status_code == 409 and r.json()["error"]["code"] == "offer_expired"
    assert (await client.get("/api/v1/flights/not-an-offer")).status_code == 409


async def test_domestic_hold_pay_and_ticket(client, sms, settings):
    tokens = (await login(client, sms))["tokens"]
    o = (await search(client, [("air_DAC", "air_CXB", DAY)], adults=1, infants=1))[0]
    infant = {"type": "infant", "given_name": "Ayan", "surname": "Uddin", "gender": "male",
              "date_of_birth": (DAY - timedelta(days=300)).isoformat()}  # fmt: skip
    r = await book(client, tokens, o, [adult(), infant], key="k-1")
    assert r.status_code == 200, r.text
    b = r.json()["data"]
    assert b["mode"] == "flight" and b["status"] == "held" and b["hold_expires_at"]
    assert b["total_amount"] == o["total"]
    assert b["base_fare"] + b["taxes"] + b["service_charge"] == b["total_amount"]
    assert b["cabin"] == "economy" and b["fare_rules"]["checked_baggage_kg"] == o["rules"]["checked_baggage_kg"]
    seg = b["segments"][0]
    assert seg["number"] == o["slices"][0]["legs"][0]["flight_number"] and seg["from_code"] == "DAC"
    assert seg["depart_at"] == o["slices"][0]["legs"][0]["depart_at"]  # local time survives the round trip
    assert [p["full_name"] for p in b["passengers"]] == ["RAHIM UDDIN", "AYAN UDDIN"]
    assert [p["type"] for p in b["passengers"]] == ["adult", "infant"]

    # Retried request: same booking, no second PNR.
    again = await book(client, tokens, o, [adult(), infant], key="k-1")
    assert again.json()["data"]["id"] == b["id"]

    payment = await start_payment(client, tokens, b["id"])
    assert payment["amount"] == b["total_amount"]
    await pay_on_mock_page(client, payment)
    done = (await client.get(f"/api/v1/bookings/{b['id']}", headers=auth(tokens))).json()["data"]
    assert done["status"] == "ticketed" and verify_qr(done["ticket"]["qr_payload"], settings)
    assert all(p["ticket_number"].startswith("000-") and len(p["ticket_number"]) == 14 for p in done["passengers"])

    pdf = await client.get(f"/api/v1/tickets/{b['id']}/pdf", headers=auth(tokens))
    assert pdf.status_code == 200 and pdf.content.startswith(b"%PDF")


async def test_international_needs_valid_passport(client, sms):
    tokens = (await login(client, sms))["tokens"]
    o = (await search(client, [("air_DAC", "air_KUL", DAY)]))[0]
    assert o["international"]

    r = await book(client, tokens, o, [adult()])
    assert r.status_code == 422 and r.json()["error"]["code"] == "passport_required"

    soon = passport(document_expiry=(DAY + timedelta(days=60)).isoformat())
    r = await book(client, tokens, o, [adult(**soon)])
    assert r.status_code == 422 and r.json()["error"]["code"] == "passport_expiry"
    assert "৬ মাস" in r.json()["error"]["message_bn"]

    r = await book(client, tokens, o, [adult(**passport())])
    assert r.status_code == 200, r.text
    assert r.json()["data"]["passengers"][0]["document_masked"].endswith("4567")


async def test_passenger_rules(client, sms):
    tokens = (await login(client, sms))["tokens"]
    o = (await search(client, [("air_DAC", "air_CGP", DAY)], adults=1, children=1))[0]

    r = await book(client, tokens, o, [adult()])
    assert r.json()["error"]["code"] == "passengers_mismatch"

    too_old = {"type": "child", "given_name": "Mim", "surname": "Akter", "gender": "female", "date_of_birth": "2000-01-01"}
    r = await book(client, tokens, o, [adult(), too_old])
    assert r.json()["error"]["code"] == "passenger_age"

    r = await book(client, tokens, o, [adult(), adult(type="child")])
    assert r.json()["error"]["code"] in ("passenger_age", "duplicate_passenger")

    bangla = adult(given="রহিম")
    r = await book(client, tokens, o, [bangla, too_old])
    assert r.status_code == 422  # names must be in Latin letters, as on the passport


async def test_price_change_is_reported(client, sms):
    tokens = (await login(client, sms))["tokens"]
    o = (await search(client, [("air_DAC", "air_CXB", DAY)]))[0]
    client.app.state.test_flight_provider.price_bump_paisa = 50000
    r = await book(client, tokens, o, [adult()])
    assert r.status_code == 409 and r.json()["error"]["code"] == "price_changed"
    new_total = (await client.get(f"/api/v1/flights/{o['id']}")).json()["data"]["total"]
    assert new_total > o["total"]
    r = await book(client, tokens, o, [adult()], expected=new_total)
    assert r.status_code == 200


async def test_expired_flight_hold_is_cancelled_with_the_airline(client, sms, store):
    from sqlalchemy import update

    from app.db.session import SessionLocal
    from app.models import Booking
    from app.services.booking import sweep_expired_holds

    tokens = (await login(client, sms))["tokens"]
    o = (await search(client, [("air_DAC", "air_CXB", DAY)]))[0]
    b = (await book(client, tokens, o, [adult()])).json()["data"]
    async with SessionLocal() as db:
        await db.execute(update(Booking).values(hold_expires_at=datetime(2020, 1, 1, tzinfo=UTC)))
        await db.commit()
        assert await sweep_expired_holds(db, client.app.state.test_providers, store) == 1
    got = (await client.get(f"/api/v1/bookings/{b['id']}", headers=auth(tokens))).json()["data"]
    assert got["status"] == "cancelled"
    pnr = next(iter(client.app.state.test_flight_provider._holds))
    assert await client.app.state.test_flight_provider.get_status(pnr) == "cancelled"
