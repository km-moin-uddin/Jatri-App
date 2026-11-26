from tests.conftest import login


async def test_send_otp_rejects_invalid_phone(client):
    r = await client.post("/api/v1/auth/otp/send", json={"phone": "0123456789"})
    assert r.status_code == 422
    body = r.json()
    assert body["success"] is False
    assert body["error"]["code"] == "invalid_phone"
    assert body["error"]["message_bn"]


async def test_send_otp_normalises_phone_and_hides_code(client, sms):
    r = await client.post("/api/v1/auth/otp/send", json={"phone": "+880 1712-345678"})
    assert r.status_code == 200
    data = r.json()["data"]
    assert data["phone"] == "01712345678"
    assert "code" not in data  # the code only goes out by SMS
    assert sms.sent[-1][0] == "01712345678"


async def test_login_creates_user_then_recognises_them(client, sms):
    first = await login(client, sms)
    assert first["is_new_user"] is True
    assert first["user"]["phone"] == "01712345678"
    assert first["tokens"]["expires_in"] == 15 * 60
    second = await login(client, sms)
    assert second["is_new_user"] is False
    assert second["user"]["id"] == first["user"]["id"]


async def test_wrong_code_counts_attempts_and_locks(client, sms):
    await client.post("/api/v1/auth/otp/send", json={"phone": "01812345678"})
    for _ in range(4):
        r = await client.post("/api/v1/auth/otp/verify", json={"phone": "01812345678", "code": "000000"})
        assert r.json()["error"]["code"] == "otp_invalid"
    r = await client.post("/api/v1/auth/otp/verify", json={"phone": "01812345678", "code": "000000"})
    assert r.status_code == 429
    assert r.json()["error"]["code"] == "otp_too_many_attempts"
    # Even the right code no longer works for this OTP.
    r = await client.post("/api/v1/auth/otp/verify", json={"phone": "01812345678", "code": sms.last_code()})
    assert r.status_code == 400


async def test_only_newest_code_is_valid(client, sms):
    await client.post("/api/v1/auth/otp/send", json={"phone": "01912345678"})
    old = sms.last_code()
    await client.post("/api/v1/auth/otp/send", json={"phone": "01912345678"})
    new = sms.last_code()
    if old != new:
        r = await client.post("/api/v1/auth/otp/verify", json={"phone": "01912345678", "code": old})
        assert r.status_code == 400
    r = await client.post("/api/v1/auth/otp/verify", json={"phone": "01912345678", "code": new})
    assert r.status_code == 200


async def test_send_is_rate_limited(client):
    for _ in range(3):
        assert (await client.post("/api/v1/auth/otp/send", json={"phone": "01312345678"})).status_code == 200
    r = await client.post("/api/v1/auth/otp/send", json={"phone": "01312345678"})
    assert r.status_code == 429
    assert r.json()["error"]["code"] == "otp_rate_limited"


async def test_resend_cooldown(client, settings):
    settings.otp_resend_cooldown_seconds = 60
    assert (await client.post("/api/v1/auth/otp/send", json={"phone": "01512345678"})).status_code == 200
    r = await client.post("/api/v1/auth/otp/send", json={"phone": "01512345678"})
    assert r.status_code == 429
    assert r.json()["error"]["code"] == "otp_cooldown"


async def test_refresh_rotates_and_detects_reuse(client, sms):
    tokens = (await login(client, sms))["tokens"]
    r = await client.post("/api/v1/auth/refresh", json={"refresh_token": tokens["refresh_token"]})
    assert r.status_code == 200
    rotated = r.json()["data"]
    assert rotated["refresh_token"] != tokens["refresh_token"]

    # Re-using the old token is treated as theft: everything for this user is revoked.
    r = await client.post("/api/v1/auth/refresh", json={"refresh_token": tokens["refresh_token"]})
    assert r.status_code == 401
    r = await client.post("/api/v1/auth/refresh", json={"refresh_token": rotated["refresh_token"]})
    assert r.status_code == 401


async def test_logout_revokes_refresh_token(client, sms):
    tokens = (await login(client, sms))["tokens"]
    assert (await client.post("/api/v1/auth/logout", json={"refresh_token": tokens["refresh_token"]})).status_code == 200
    r = await client.post("/api/v1/auth/refresh", json={"refresh_token": tokens["refresh_token"]})
    assert r.status_code == 401
    assert r.json()["error"]["code"] == "invalid_refresh_token"
