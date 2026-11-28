from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware

from app.api.v1 import auth, bookings, buses, engagement, flights, locations, me, notifications, payments, trains
from app.core.config import get_settings
from app.core.envelope import install_error_handlers, ok

# Sent with every response. The API returns JSON (and PDFs) only; nothing should frame or sniff it.
_SECURITY_HEADERS = {
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Referrer-Policy": "no-referrer",
    "Content-Security-Policy": "default-src 'none'; frame-ancestors 'none'",
}
# The sandbox checkout page has inline styles and posts a form back to us.
_SANDBOX_CSP = "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'"


def create_app() -> FastAPI:
    settings = get_settings()
    app = FastAPI(
        title="Jatri API",
        version="0.9.0",
        description="Flight, bus and train ticket booking for Bangladesh. All responses use the "
        "`{success, data, error}` envelope.",
        # The interactive docs are for development; production doesn't advertise its API.
        openapi_url=None if settings.is_prod else "/api/v1/openapi.json",
        docs_url=None if settings.is_prod else "/docs",
        redoc_url=None,
    )
    install_error_handlers(app)
    if settings.cors_origins:
        app.add_middleware(CORSMiddleware, allow_origins=settings.cors_origins, allow_methods=["*"], allow_headers=["*"])

    @app.middleware("http")
    async def security_headers(request: Request, call_next):
        response = await call_next(request)
        for k, v in _SECURITY_HEADERS.items():
            response.headers.setdefault(k, v)
        path = request.url.path
        if path.startswith("/api/v1/payments/mock/"):
            response.headers["Content-Security-Policy"] = _SANDBOX_CSP
        elif path == "/docs":
            del response.headers["Content-Security-Policy"]  # Swagger UI loads its own scripts (dev only)
        if settings.is_prod:
            response.headers["Strict-Transport-Security"] = "max-age=31536000; includeSubDomains"
        # Personal data (tickets, profiles, tokens) must not sit in shared caches.
        response.headers.setdefault("Cache-Control", "no-store")
        return response

    for router in (
        auth.router,
        me.router,
        locations.router,
        buses.router,
        flights.router,
        trains.router,
        bookings.router,
        payments.router,
        notifications.router,
        engagement.router,
    ):
        app.include_router(router, prefix="/api/v1")

    @app.get("/health", tags=["meta"])
    async def health():
        return ok({"status": "ok"})

    return app


app = create_app()
