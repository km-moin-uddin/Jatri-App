from app.core.phone import normalize_bd_phone


async def test_locations_by_type(client):
    r = await client.get("/api/v1/locations", params={"type": "airport"})
    data = r.json()["data"]
    assert len(data) == 15
    assert {"DAC", "CXB", "CGP", "ZYL", "JSR", "SPD", "RJH", "BZL"} <= {d["code"] for d in data}
    assert data[0]["is_popular"] is True  # popular first


async def test_locations_search_bangla_and_code(client):
    r = await client.get("/api/v1/locations", params={"q": "কক্স"})
    keys = {d["id"] for d in r.json()["data"]}
    assert {"air_CXB", "bus_coxsbazar", "stn_coxsbazar"} <= keys
    r = await client.get("/api/v1/locations", params={"type": "airport", "q": "zyl"})
    assert [d["id"] for d in r.json()["data"]] == ["air_ZYL"]


async def test_unknown_route_uses_envelope(client):
    r = await client.get("/api/v1/nope")
    assert r.status_code == 404
    assert r.json() == {
        "success": False,
        "data": None,
        "error": {"code": "not_found", "message_en": "Not found.", "message_bn": "খুঁজে পাওয়া যায়নি।"},
    }


def test_phone_normalisation():
    assert normalize_bd_phone("01712345678") == "01712345678"
    assert normalize_bd_phone("+8801712345678") == "01712345678"
    assert normalize_bd_phone("৮৮০১৭১২৩৪৫৬৭৮") == "01712345678"
    assert normalize_bd_phone("01212345678") is None
    assert normalize_bd_phone("1712345678") is None
