from tests.conftest import login


def auth(tokens: dict) -> dict:
    return {"Authorization": f"Bearer {tokens['access_token']}"}


async def test_me_requires_token(client):
    r = await client.get("/api/v1/me")
    assert r.status_code == 401
    assert r.json()["error"]["code"] == "unauthorized"
    r = await client.get("/api/v1/me", headers={"Authorization": "Bearer not-a-jwt"})
    assert r.status_code == 401


async def test_update_profile(client, sms):
    tokens = (await login(client, sms))["tokens"]
    r = await client.patch("/api/v1/me", json={"name": "  Rahim   Uddin ", "language": "en"}, headers=auth(tokens))
    assert r.status_code == 200
    assert r.json()["data"]["name"] == "Rahim Uddin"
    assert r.json()["data"]["language"] == "en"
    r = await client.get("/api/v1/me", headers=auth(tokens))
    assert r.json()["data"]["name"] == "Rahim Uddin"


async def test_update_profile_validates(client, sms):
    tokens = (await login(client, sms))["tokens"]
    r = await client.patch("/api/v1/me", json={"email": "not-an-email"}, headers=auth(tokens))
    assert r.status_code == 422
    assert r.json()["error"]["code"] == "validation_error"


async def test_delete_account(client, sms):
    data = await login(client, sms)
    tokens = data["tokens"]
    assert (await client.delete("/api/v1/me", headers=auth(tokens))).status_code == 200
    assert (await client.get("/api/v1/me", headers=auth(tokens))).status_code == 401
    r = await client.post("/api/v1/auth/refresh", json={"refresh_token": tokens["refresh_token"]})
    assert r.status_code == 401
    # The same phone can sign up again as a new user.
    again = await login(client, sms)
    assert again["is_new_user"] is True
