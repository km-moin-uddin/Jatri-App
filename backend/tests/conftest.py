"""Test setup. Uses SQLite by default; set JATRI_TEST_DATABASE_URL to run against PostgreSQL."""

import os

os.environ["JATRI_ENV"] = "test"
os.environ["JATRI_DATABASE_URL"] = os.environ.get("JATRI_TEST_DATABASE_URL", "sqlite+aiosqlite:///./.test.db")

import uuid  # noqa: E402

import pytest  # noqa: E402
from httpx import ASGITransport, AsyncClient  # noqa: E402
from sqlalchemy import insert  # noqa: E402

from app.api.deps import sms_sender  # noqa: E402
from app.core.config import Settings, get_settings  # noqa: E402
from app.db.base import Base  # noqa: E402
from app.db.session import engine  # noqa: E402
from app.main import create_app  # noqa: E402
from app.models import Location  # noqa: E402
from app.providers.mock_bus import MockBusProvider, get_bus_provider  # noqa: E402
from app.providers.mock_flight import MockFlightProvider, get_flight_provider  # noqa: E402
from app.providers.mock_train import MockTrainProvider, get_train_provider  # noqa: E402
from app.providers.registry import Providers  # noqa: E402
from app.seed import load_locations  # noqa: E402
from app.services.cache import MemoryStore, RedisStore, get_store  # noqa: E402


class FakeSms:
    """Captures SMS so tests can read the OTP."""

    def __init__(self):
        self.sent: list[tuple[str, str]] = []

    async def send(self, phone: str, text: str) -> None:
        self.sent.append((phone, text))

    def last_code(self) -> str:
        text = self.sent[-1][1]
        return text.split("code: ")[1][:6]


@pytest.fixture
async def db_schema():
    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.drop_all)
        await conn.run_sync(Base.metadata.create_all)
        await conn.execute(insert(Location), [{"id": uuid.uuid4(), **row} for row in load_locations()])
    yield


@pytest.fixture
def settings() -> Settings:
    # No resend cooldown by default so tests can send codes back to back.
    return Settings(env="test", secret_key="test-secret-key-0123456789-abcdef", otp_resend_cooldown_seconds=0)


@pytest.fixture
def sms() -> FakeSms:
    return FakeSms()


@pytest.fixture
async def store():
    """Fresh in-memory store, or a real Redis when JATRI_TEST_REDIS_URL is set (as in CI)."""
    url = os.environ.get("JATRI_TEST_REDIS_URL")
    if url:
        s = RedisStore(url)
        await s._redis.flushdb()
        yield s
        await s._redis.aclose()
    else:
        yield MemoryStore()


@pytest.fixture
async def client(db_schema, settings, sms, store):
    app = create_app()
    provider = MockBusProvider()
    flight = MockFlightProvider()
    train = MockTrainProvider()
    app.state.test_provider = provider
    app.state.test_flight_provider = flight
    app.state.test_train_provider = train
    app.state.test_providers = Providers(provider, flight, train)
    app.dependency_overrides[get_settings] = lambda: settings
    app.dependency_overrides[sms_sender] = lambda: sms
    app.dependency_overrides[get_store] = lambda: store
    app.dependency_overrides[get_bus_provider] = lambda: provider
    app.dependency_overrides[get_flight_provider] = lambda: flight
    app.dependency_overrides[get_train_provider] = lambda: train
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as c:
        c.app = app
        yield c


async def login(client: AsyncClient, sms: FakeSms, phone: str = "01712345678") -> dict:
    r = await client.post("/api/v1/auth/otp/send", json={"phone": phone})
    assert r.status_code == 200, r.text
    r = await client.post("/api/v1/auth/otp/verify", json={"phone": phone, "code": sms.last_code()})
    assert r.status_code == 200, r.text
    return r.json()["data"]
