from datetime import UTC, date, datetime, timedelta

from sqlalchemy import text, update

from app.db.session import engine
from app.models import Booking
from tests.conftest import login

TOMORROW = (date.today() + timedelta(days=1)).isoformat()


def auth(tokens: dict) -> dict:
    return {"Authorization": f"Bearer {tokens['access_token']}"}


async def search(client, **extra):
    body = {"from_id": "bus_dhaka", "to_id": "bus_coxsbazar", "date": TOMORROW, **extra}
    r = await client.post("/api/v1/buses/search", json=body)
    assert r.status_code == 200, r.text
    return r.json()["data"]


async def seat_map(client, trip_id):
    r = await client.get(f"/api/v1/buses/{trip_id}/seats")
    assert r.status_code == 200, r.text
    return r.json()["data"]


def seats_with_state(seat_map_data, state):
    return [s["number"] for row in seat_map_data["rows"] for s in row if s and s["state"] == state]


async def hold_body(client, n=2, gender="male"):
    trip = (await search(client))[0]
    sm = await seat_map(client, trip["id"])
    seats = seats_with_state(sm, "available")[:n]
    return trip, {
        "trip_id": trip["id"],
        "seats": seats,
        "boarding_point_id": trip["boarding_points"][0]["id"],
        "dropping_point_id": trip["dropping_points"][0]["id"],
        "passengers": [{"full_name": f"Passenger {i}", "gender": gender, "age": 30, "seat_no": s} for i, s in enumerate(seats)],
        "contact_phone": "01712345678",
        "expected_total": (trip["fare"] + sm["service_charge_per_seat"]) * n,
    }


async def test_search_is_stable_and_cached(client, store):
    first = await search(client)
    second = await search(client)
    assert first == second
    assert 1 <= len(first) <= 5
    trip = first[0]
    assert trip["fare"] % 100 == 0 and trip["fare"] >= 30000  # paisa
    assert trip["boarding_points"] and trip["dropping_points"]
    assert await store.get_json(f"jatri:bus:search:mock_bus:bus_dhaka:bus_coxsbazar:{TOMORROW}:any") is not None


async def test_search_filters_and_rejects_nonsense(client):
    assert all(t["coach_type"] == "ac" for t in await search(client, coach_type="ac"))
    r = await client.post("/api/v1/buses/search", json={"from_id": "bus_dhaka", "to_id": "bus_dhaka", "date": TOMORROW})
    assert r.json()["data"] == []


async def test_seat_map_has_ladies_and_booked_seats(client):
    trip = (await search(client))[0]
    sm = await seat_map(client, trip["id"])
    assert sm["max_selectable"] == 4
    assert seats_with_state(sm, "available")
    assert any(None in row for row in sm["rows"])  # aisle


async def test_booking_requires_login(client):
    _, body = await hold_body(client)
    r = await client.post("/api/v1/bookings", json=body)
    assert r.status_code == 401


async def test_hold_happy_path(client, sms):
    tokens = (await login(client, sms))["tokens"]
    trip, body = await hold_body(client)
    body["passengers"][0]["document_no"] = "1234567890"
    r = await client.post("/api/v1/bookings", json=body, headers=auth(tokens))
    assert r.status_code == 200, r.text
    b = r.json()["data"]
    assert b["status"] == "held"
    assert b["booking_ref"].startswith("JT") and len(b["booking_ref"]) == 6
    assert b["total_amount"] == body["expected_total"]
    assert b["base_fare"] == trip["fare"] * 2
    assert b["segments"][0]["seat_numbers"] == sorted(body["seats"])
    assert b["segments"][0]["boarding_point"]["id"] == body["boarding_point_id"]
    assert b["passengers"][0]["document_masked"] == "••••••7890"
    assert b["hold_expires_at"]

    # NID is encrypted in the database, never stored in plain text.
    async with engine.connect() as conn:
        raw = (await conn.execute(text("select document_no from booking_passengers where document_no is not null"))).scalar_one()
    assert "1234567890" not in raw

    # Held seats show as unavailable to everyone.
    sm = await seat_map(client, trip["id"])
    held = set(seats_with_state(sm, "held"))
    assert set(body["seats"]) <= held


async def test_idempotency_key_returns_same_booking(client, sms):
    tokens = (await login(client, sms))["tokens"]
    _, body = await hold_body(client)
    headers = {**auth(tokens), "Idempotency-Key": "abc-123"}
    a = (await client.post("/api/v1/bookings", json=body, headers=headers)).json()["data"]
    b = (await client.post("/api/v1/bookings", json=body, headers=headers)).json()["data"]
    assert a["id"] == b["id"]
    r = await client.get("/api/v1/bookings", headers=auth(tokens))
    assert len(r.json()["data"]) == 1


async def test_two_people_cannot_hold_the_same_seat(client, sms):
    alice = (await login(client, sms, "01712345678"))["tokens"]
    bob = (await login(client, sms, "01812345678"))["tokens"]
    _, body = await hold_body(client)
    assert (await client.post("/api/v1/bookings", json=body, headers=auth(alice))).status_code == 200
    r = await client.post("/api/v1/bookings", json=body, headers=auth(bob))
    assert r.status_code == 409
    assert r.json()["error"]["code"] == "seat_unavailable"


async def test_price_change_is_reported(client, sms):
    tokens = (await login(client, sms))["tokens"]
    _, body = await hold_body(client)
    body["expected_total"] -= 100
    r = await client.post("/api/v1/bookings", json=body, headers=auth(tokens))
    assert r.status_code == 409
    assert r.json()["error"]["code"] == "price_changed"
    assert "৳" in r.json()["error"]["message_bn"]


async def test_booked_and_ladies_seat_rules(client, sms):
    tokens = (await login(client, sms))["tokens"]
    trip, body = await hold_body(client, n=1)
    sm = await seat_map(client, trip["id"])
    booked = seats_with_state(sm, "booked")[0]
    body["seats"] = [booked]
    body["passengers"][0]["seat_no"] = booked
    body["expected_total"] = None
    r = await client.post("/api/v1/bookings", json=body, headers=auth(tokens))
    assert r.json()["error"]["code"] == "seat_unavailable"

    ladies = seats_with_state(sm, "ladies")
    if ladies:
        body["seats"] = [ladies[0]]
        body["passengers"][0]["seat_no"] = ladies[0]
        r = await client.post("/api/v1/bookings", json=body, headers=auth(tokens))
        assert r.json()["error"]["code"] == "ladies_seat"
        body["passengers"][0]["gender"] = "female"
        r = await client.post("/api/v1/bookings", json=body, headers=auth(tokens))
        assert r.status_code == 200, r.text


async def test_passengers_must_match_seats(client, sms):
    tokens = (await login(client, sms))["tokens"]
    _, body = await hold_body(client)
    body["passengers"] = body["passengers"][:1]
    r = await client.post("/api/v1/bookings", json=body, headers=auth(tokens))
    assert r.status_code == 422


async def test_other_users_cannot_see_my_booking(client, sms):
    alice = (await login(client, sms, "01712345678"))["tokens"]
    bob = (await login(client, sms, "01812345678"))["tokens"]
    _, body = await hold_body(client)
    booking_id = (await client.post("/api/v1/bookings", json=body, headers=auth(alice))).json()["data"]["id"]
    r = await client.get(f"/api/v1/bookings/{booking_id}", headers=auth(bob))
    assert r.status_code == 404
    assert (await client.post(f"/api/v1/bookings/{booking_id}/cancel", headers=auth(bob))).status_code == 404


async def test_cancel_releases_seats(client, sms):
    alice = (await login(client, sms, "01712345678"))["tokens"]
    bob = (await login(client, sms, "01812345678"))["tokens"]
    _, body = await hold_body(client)
    booking_id = (await client.post("/api/v1/bookings", json=body, headers=auth(alice))).json()["data"]["id"]
    r = await client.post(f"/api/v1/bookings/{booking_id}/cancel", headers=auth(alice))
    assert r.json()["data"]["status"] == "cancelled"
    assert (await client.post("/api/v1/bookings", json=body, headers=auth(bob))).status_code == 200


async def test_expired_hold_is_cancelled_and_seats_freed(client, sms, store):
    alice = (await login(client, sms, "01712345678"))["tokens"]
    bob = (await login(client, sms, "01812345678"))["tokens"]
    _, body = await hold_body(client)
    booking_id = (await client.post("/api/v1/bookings", json=body, headers=auth(alice))).json()["data"]["id"]

    async with engine.begin() as conn:
        await conn.execute(update(Booking).values(hold_expires_at=datetime.now(UTC) - timedelta(minutes=1)))
    r = await client.get(f"/api/v1/bookings/{booking_id}", headers=auth(alice))
    assert r.json()["data"]["status"] == "cancelled"
    assert (await client.post("/api/v1/bookings", json=body, headers=auth(bob))).status_code == 200


async def test_store_holds_all_seats_or_none(store):
    assert await store.acquire_all(["s:1", "s:2"], "a", 60)
    assert not await store.acquire_all(["s:2", "s:3"], "b", 60)  # s:2 taken
    assert await store.owners(["s:3"]) == {}  # nothing partial left behind
    await store.release(["s:1", "s:2"], "b")  # not b's to release
    assert set((await store.owners(["s:1", "s:2"])).values()) == {"a"}
    await store.release(["s:1", "s:2"], "a")
    assert await store.acquire_all(["s:2", "s:3"], "b", 60)
