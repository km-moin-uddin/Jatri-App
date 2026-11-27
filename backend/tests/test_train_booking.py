from datetime import UTC, datetime, timedelta

from sqlalchemy import update

from app.db.session import SessionLocal
from app.models import Booking
from app.providers.mock_train import BD_TZ
from app.services.booking import sweep_expired_holds
from app.services.ticket import verify_qr
from tests.conftest import login
from tests.test_bus_booking import auth
from tests.test_payments import pay_on_mock_page, start_payment


def bd_today():
    return datetime.now(UTC).astimezone(BD_TZ).date()


DAY = bd_today() + timedelta(days=5)


async def search(client, day=DAY, travel_class=None, a="stn_dhaka", b="stn_chattogram"):
    body = {"from_id": a, "to_id": b, "date": day.isoformat()} | ({"travel_class": travel_class} if travel_class else {})
    return await client.post("/api/v1/trains/search", json=body)


async def seats(client, trip_id, travel_class):
    r = await client.get(f"/api/v1/trains/{trip_id}/seats", params={"travel_class": travel_class})
    assert r.status_code == 200, r.text
    return r.json()["data"]


def free(seat_map, state="available"):
    return [s["number"] for c in seat_map["coaches"] for row in c["rows"] for s in row if s and s["state"] == state]


async def held_train(client, sms, n=2, ages=(34, 30), nids=("1234567890", "1234567890123")):
    tokens = (await login(client, sms))["tokens"]
    trip = next(t for t in (await search(client)).json()["data"] if any(c["travel_class"] == "snigdha" for c in t["classes"]))
    sm = await seats(client, trip["id"], "snigdha")
    chosen = free(sm)[:n]
    body = {
        "mode": "train",
        "trip_id": trip["id"],
        "travel_class": "snigdha",
        "seats": chosen,
        "passengers": [
            {"full_name": f"Passenger {i}", "gender": "male", "age": ages[i], "seat_no": s, "document_no": nids[i]}
            for i, s in enumerate(chosen)
        ],
        "contact_phone": "01712345678",
        "expected_total": (sm["fare"] + sm["service_charge_per_seat"]) * n,
    }
    return tokens, trip, sm, body


async def test_search_lists_classes_fares_and_off_days(client):
    r = await search(client)
    assert r.status_code == 200, r.text
    trips = r.json()["data"]
    assert trips
    t = trips[0]
    assert t["depart_at"].endswith("+06:00")
    assert {c["travel_class"] for t in trips for c in t["classes"]} >= {"shovan_chair", "snigdha"}
    # Dhaka-Chattogram Shovan Chair ≈ ৳345 on the railway; the mock follows the per-km ratio.
    chair = next(c for t in trips for c in t["classes"] if c["travel_class"] == "shovan_chair")
    assert 300_00 <= chair["fare"] <= 400_00
    # A train never shows up on its weekly off day.
    weekday = ["monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"][DAY.weekday()]
    assert all(t["off_day"] != weekday for t in trips)
    only = (await search(client, travel_class="ac_berth")).json()["data"]
    assert all(any(c["travel_class"] == "ac_berth" for c in t["classes"]) for t in only)


async def test_sale_window_is_ten_days(client):
    r = await search(client, day=bd_today() + timedelta(days=11))
    assert r.status_code == 422 and r.json()["error"]["code"] == "outside_sale_window"
    assert (await search(client, day=bd_today() - timedelta(days=1))).status_code == 422
    assert (await search(client, day=bd_today() + timedelta(days=10))).status_code == 200


async def test_coach_seat_map_matches_availability(client):
    trip = (await search(client)).json()["data"][0]
    klass = trip["classes"][0]
    sm = await seats(client, trip["id"], klass["travel_class"])
    assert sm["coaches"] and all(row.count(None) == 1 for c in sm["coaches"] for row in c["rows"])
    assert len(free(sm)) == klass["seats_available"]
    assert all(n.split("-")[0] in {c["name"] for c in sm["coaches"]} for n in free(sm))
    r = await client.get(
        f"/api/v1/trains/{trip['id']}/seats", params={"travel_class": "f_seat" if klass["travel_class"] != "f_seat" else "x"}
    )
    assert r.status_code in (404, 422)


async def test_hold_pay_and_ticket(client, sms, settings):
    tokens, trip, sm, body = await held_train(client, sms)
    r = await client.post("/api/v1/bookings", json=body, headers=auth(tokens) | {"Idempotency-Key": "t-1"})
    assert r.status_code == 200, r.text
    b = r.json()["data"]
    assert b["mode"] == "train" and b["status"] == "held"
    seg = b["segments"][0]
    assert (
        seg["travel_class"] == "snigdha"
        and seg["coach"]
        and seg["seat_numbers"] == sorted(body["seats"], key=lambda n: (n.split("-")[0], int(n.split("-")[1])))
    )
    assert b["total_amount"] == body["expected_total"]
    assert b["passengers"][0]["document_masked"].endswith("7890")

    # Others now see the seats as held; a retry returns the same booking.
    assert set(body["seats"]) <= set(free(await seats(client, trip["id"], "snigdha"), "held"))
    again = await client.post("/api/v1/bookings", json=body, headers=auth(tokens) | {"Idempotency-Key": "t-1"})
    assert again.json()["data"]["id"] == b["id"]

    payment = await start_payment(client, tokens, b["id"])
    await pay_on_mock_page(client, payment)
    done = (await client.get(f"/api/v1/bookings/{b['id']}", headers=auth(tokens))).json()["data"]
    assert done["status"] == "ticketed" and verify_qr(done["ticket"]["qr_payload"], settings)
    assert set(body["seats"]) <= set(free(await seats(client, trip["id"], "snigdha"), "booked"))
    pdf = await client.get(f"/api/v1/tickets/{b['id']}/pdf", headers=auth(tokens))
    assert pdf.status_code == 200 and pdf.content.startswith(b"%PDF")


async def test_nid_rules(client, sms):
    tokens, _, _, body = await held_train(client, sms, ages=(34, 20), nids=("1234567890", None))
    r = await client.post("/api/v1/bookings", json=body, headers=auth(tokens))
    assert r.status_code == 422 and r.json()["error"]["code"] == "nid_required"
    assert "এনআইডি" in r.json()["error"]["message_bn"]
    # A 10-year-old needs no NID.
    body["passengers"][1] |= {"age": 10, "document_no": None}
    r = await client.post("/api/v1/bookings", json=body, headers=auth(tokens))
    assert r.status_code == 200, r.text


async def test_conflicts_price_and_expiry(client, sms, store):
    tokens, trip, sm, body = await held_train(client, sms, n=1, ages=(30,), nids=("1234567890",))
    wrong = body | {"expected_total": body["expected_total"] + 100}
    r = await client.post("/api/v1/bookings", json=wrong, headers=auth(tokens))
    assert r.status_code == 409 and r.json()["error"]["code"] == "price_changed"
    assert (await client.post("/api/v1/bookings", json=body, headers=auth(tokens))).status_code == 200
    # Someone else can't take the same seat while it's held.
    other = (await login(client, sms, "01812345678"))["tokens"]
    r = await client.post("/api/v1/bookings", json=body, headers=auth(other))
    assert r.status_code == 409 and r.json()["error"]["code"] == "seat_unavailable"
    async with SessionLocal() as db:
        await db.execute(update(Booking).values(hold_expires_at=datetime(2020, 1, 1, tzinfo=UTC)))
        await db.commit()
        assert await sweep_expired_holds(db, client.app.state.test_providers, store) == 1
    assert (await client.post("/api/v1/bookings", json=body, headers=auth(other))).status_code == 200
