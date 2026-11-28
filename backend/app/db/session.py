from collections.abc import AsyncIterator

from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.core.config import get_settings

_settings = get_settings()
_pool = (
    {}
    if _settings.database_url.startswith("sqlite")
    else {"pool_size": _settings.db_pool_size, "max_overflow": _settings.db_max_overflow, "pool_recycle": 1800}
)
# Per worker process. Keep workers × (pool_size + max_overflow) under PostgreSQL's max_connections.
engine = create_async_engine(_settings.database_url, pool_pre_ping=True, **_pool)
SessionLocal = async_sessionmaker(engine, expire_on_commit=False)


async def get_session() -> AsyncIterator[AsyncSession]:
    async with SessionLocal() as session:
        yield session
