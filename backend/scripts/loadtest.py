"""Small load test: many users booking bus tickets at once against a running API.

Each virtual user: OTP login → bus search → seat map → hold → mock payment → My Trips.
Needs a dev/staging server with JATRI_DEV_FIXED_OTP set, the mock payment gateway and, since all
users come from one IP, JATRI_RATE_LIMITS=false. Never point it at production.

    uv run python scripts/loadtest.py --base http://localhost:8000 --users 50 --rounds 2
"""

import argparse
import asyncio
import random
import statistics
import time
from collections import defaultdict
from datetime import date, timedelta

import httpx

RUN = int(time.time())  # fresh idempotency keys on every run
timings: dict[str, list[float]] = defaultdict(list)
errors: dict[str, int] = defaultdict(int)


async def timed(name: str, coro):
    start = time.perf_counter()
    try:
        r = await coro
    except httpx.HTTPError:
        errors[name] += 1
        return None
    timings[name].append((time.perf_counter() - start) * 1000)
    if r.status_code >= 400:
        errors[name] += 1
        return None
    return r


async def user(client: httpx.AsyncClient, n: int, otp: str) -> None:
    phone = f"017{n:08d}"
    if not await timed("otp_send", client.post("/api/v1/auth/otp/send", json={"phone": phone})):
        return
    r = await timed("otp_verify", client.post("/api/v1/auth/otp/verify", json={"phone": phone, "code": otp}))
    if not r:
        return
    h = {"Authorization": f"Bearer {r.json()['data']['tokens']['access_token']}"}
    day = (date.today() + timedelta(days=random.randint(2, 20))).isoformat()
    body = {"from_id": "bus_dhaka", "to_id": "bus_coxsbazar", "date": day}
    r = await timed("bus_search", client.post("/api/v1/buses/search", json=body))
    if not r or not r.json()["data"]:
        return
    trip = random.choice(r.json()["data"])
    r = await timed("seat_map", client.get(f"/api/v1/buses/{trip['id']}/seats"))
    if not r:
        return
    seats = [s["number"] for row in r.json()["data"]["rows"] for s in row if s and s["state"] == "available"]
    if not seats:
        return
    seat = random.choice(seats)
    hold = {
        "trip_id": trip["id"],
        "seats": [seat],
        "boarding_point_id": trip["boarding_points"][0]["id"],
        "dropping_point_id": trip["dropping_points"][0]["id"],
        "passengers": [{"full_name": "Load Test", "gender": "male", "age": 30, "seat_no": seat}],
        "contact_phone": phone,
        "expected_total": trip["fare"] + r.json()["data"]["service_charge_per_seat"],
    }
    r = await timed("hold", client.post("/api/v1/bookings", json=hold, headers=h | {"Idempotency-Key": f"lt-{RUN}-{n}"}))
    if not r:  # someone else took the seat first: that's expected under load
        return
    booking = r.json()["data"]
    pay = {"booking_id": booking["id"], "method": "bkash"}
    r = await timed("pay_init", client.post("/api/v1/payments/init", json=pay, headers=h))
    if r:
        pid = r.json()["data"]["payment_id"]
        await timed("pay_complete", client.post(f"/api/v1/payments/mock/complete/{pid}", data={"result": "success"}))
    await timed("my_trips", client.get("/api/v1/bookings", headers=h))


async def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8000")
    ap.add_argument("--users", type=int, default=50)
    ap.add_argument("--rounds", type=int, default=1)
    ap.add_argument("--otp", default="123456")
    a = ap.parse_args()
    limits = httpx.Limits(max_connections=a.users)
    async with httpx.AsyncClient(base_url=a.base, timeout=30, limits=limits) as client:
        start = time.perf_counter()
        for r in range(a.rounds):
            await asyncio.gather(*(user(client, r * 100_000 + i, a.otp) for i in range(a.users)))
        took = time.perf_counter() - start
    print(f"{a.users} users × {a.rounds} rounds in {took:.1f}s\n")
    print(f"{'step':<14}{'count':>7}{'errors':>8}{'p50 ms':>9}{'p95 ms':>9}{'max ms':>9}")
    for name, ts in timings.items():
        ts.sort()
        p95 = ts[min(len(ts) - 1, int(len(ts) * 0.95))]
        print(f"{name:<14}{len(ts):>7}{errors[name]:>8}{statistics.median(ts):>9.0f}{p95:>9.0f}{ts[-1]:>9.0f}")


if __name__ == "__main__":
    asyncio.run(main())
