from fastapi import FastAPI

from app.api.v1 import auth, bookings, buses, locations, me
from app.core.envelope import install_error_handlers, ok


def create_app() -> FastAPI:
    app = FastAPI(
        title="Jatri API",
        version="0.2.0",
        description="Flight, bus and train ticket booking for Bangladesh. All responses use the "
        "`{success, data, error}` envelope.",
        openapi_url="/api/v1/openapi.json",
        docs_url="/docs",
    )
    install_error_handlers(app)
    for router in (auth.router, me.router, locations.router, buses.router, bookings.router):
        app.include_router(router, prefix="/api/v1")

    @app.get("/health", tags=["meta"])
    async def health():
        return ok({"status": "ok"})

    return app


app = create_app()
