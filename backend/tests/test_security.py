from app.core.config import Settings
from app.main import create_app
from tests.conftest import login
from tests.test_bus_booking import auth
from tests.test_payments import held_booking


async def test_security_headers(client):
    r = await client.get("/health")
    assert r.headers["x-content-type-options"] == "nosniff"
    assert r.headers["x-frame-options"] == "DENY"
    assert r.headers["cache-control"] == "no-store"
    assert "default-src 'none'" in r.headers["content-security-policy"]
    assert "strict-transport-security" not in r.headers  # only in production


async def test_otp_send_is_limited_per_ip(client, sms):
    # 10 sends per 10 minutes from one address, whatever the phone numbers (SMS pumping).
    codes = []
    for i in range(11):
        r = await client.post("/api/v1/auth/otp/send", json={"phone": f"0171234{i:04d}"})
        codes.append(r.status_code)
    assert codes[:10] == [200] * 10
    assert codes[10] == 429
    assert r.json()["error"]["code"] == "rate_limited" and int(r.headers["retry-after"]) > 0


async def test_promo_guessing_is_limited_per_user(client, sms):
    tokens, _, _, b = await held_booking(client, sms)
    statuses = [
        (await client.post(f"/api/v1/bookings/{b['id']}/promo", json={"code": f"GUESS{i}"}, headers=auth(tokens))).status_code
        for i in range(11)
    ]
    assert statuses[:10] == [422] * 10 and statuses[10] == 429


async def test_wrong_otp_attempts_lock_the_code(client, sms):
    await client.post("/api/v1/auth/otp/send", json={"phone": "01712345678"})
    for _ in range(5):
        r = await client.post("/api/v1/auth/otp/verify", json={"phone": "01712345678", "code": "000000"})
    assert r.json()["error"]["code"] == "otp_too_many_attempts"
    # Even the right code no longer works: a new one must be requested.
    r = await client.post("/api/v1/auth/otp/verify", json={"phone": "01712345678", "code": sms.last_code()})
    assert r.status_code != 200


async def test_other_users_data_is_not_reachable(client, sms):
    tokens, _, _, b = await held_booking(client, sms)
    other = (await login(client, sms, "01812345678"))["tokens"]
    for path in (f"/api/v1/bookings/{b['id']}", f"/api/v1/bookings/{b['id']}/cancel-quote", f"/api/v1/tickets/{b['id']}/pdf"):
        r = await client.get(path, headers=auth(other))
        assert r.status_code == 404, path
    r = await client.post(f"/api/v1/bookings/{b['id']}/promo", json={"code": "JATRI150"}, headers=auth(other))
    assert r.status_code == 404
    r = await client.post("/api/v1/payments/init", json={"booking_id": b["id"], "method": "bkash"}, headers=auth(other))
    assert r.status_code == 404


async def test_tampered_or_expired_tokens_are_rejected(client, sms):
    tokens = (await login(client, sms))["tokens"]
    bad = tokens["access_token"][:-2] + ("AA" if not tokens["access_token"].endswith("AA") else "BB")
    assert (await client.get("/api/v1/me", headers={"Authorization": f"Bearer {bad}"})).status_code == 401
    import jwt

    none_alg = jwt.encode({"sub": "x", "type": "access"}, key=None, algorithm="none")
    assert (await client.get("/api/v1/me", headers={"Authorization": f"Bearer {none_alg}"})).status_code == 401
    # A refresh token can't be used as an access token.
    assert (await client.get("/api/v1/me", headers={"Authorization": f"Bearer {tokens['refresh_token']}"})).status_code == 401


def test_production_hides_docs(monkeypatch):
    prod = Settings(
        env="prod",
        secret_key="x" * 40,
        data_key="k",
        redis_url="redis://r",
        payment_gateway="sslcommerz",
        sslcommerz_sandbox=False,
    )
    monkeypatch.setattr("app.main.get_settings", lambda: prod)
    app = create_app()
    assert app.docs_url is None and app.openapi_url is None
