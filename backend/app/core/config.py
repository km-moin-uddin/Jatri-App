"""App settings, read from environment variables (see .env.example). No secrets in code."""

from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_prefix="JATRI_", extra="ignore")

    env: str = Field(default="dev", description="dev | test | prod")
    database_url: str = "postgresql+asyncpg://jatri:jatri@localhost:5432/jatri"

    # Signing key for access tokens and pepper for hashing OTPs / refresh tokens.
    # Must be set to a long random value in production (e.g. `openssl rand -hex 32`).
    secret_key: str = "dev-only-change-me"  # noqa: S105 - placeholder, rejected in prod below

    access_token_minutes: int = 15
    refresh_token_days: int = 30

    otp_length: int = 6
    otp_ttl_seconds: int = 300
    otp_max_attempts: int = 5
    otp_max_sends_per_window: int = 3
    otp_send_window_seconds: int = 600
    otp_resend_cooldown_seconds: int = 60
    # DEV ONLY: when set, every OTP is this value so you can log in without an SMS gateway.
    dev_fixed_otp: str | None = None
    # Appended to the SMS so Android's SMS Retriever API can read the code automatically.
    android_app_hash: str | None = None

    sms_provider: str = Field(default="console", description="console | (real gateway adapter later)")

    # Redis: search cache + seat-hold locks. Without it an in-process fallback is used (dev/tests only).
    redis_url: str | None = None
    search_cache_seconds: int = 300

    # Fernet key for personal data at rest (NID / passport numbers). `python -c "from cryptography.fernet
    # import Fernet; print(Fernet.generate_key().decode())"`. The dev default is derived from secret_key.
    data_key: str | None = None

    # Payments. "mock" = built-in sandbox checkout page (no credentials); "sslcommerz" = SSLCommerz.
    payment_gateway: str = "mock"
    sslcommerz_store_id: str | None = None
    sslcommerz_store_password: str | None = None  # secret: set in the deploy platform, never commit
    sslcommerz_sandbox: bool = True
    # Public URL of this API (gateways call back here) and where the browser returns to the app.
    public_base_url: str = "http://localhost:8000"
    app_return_url: str = "jatri://payment/return"

    # Push (FCM HTTP v1). "console" logs instead. The service-account JSON is a secret: set it in the
    # deploy platform (the whole JSON, or a path to the file), never commit it.
    push_provider: str = Field(default="console", description="console | fcm")
    fcm_project_id: str | None = None
    fcm_service_account: str | None = None

    # E-mail with the PDF ticket. "console" logs instead; "smtp" uses the settings below.
    email_provider: str = Field(default="console", description="console | smtp")
    smtp_host: str | None = None
    smtp_port: int = 587
    smtp_user: str | None = None
    smtp_password: str | None = None  # secret
    email_from: str = "Jatri <tickets@example.com>"

    # Booking
    hold_minutes: int = 10
    max_bus_seats: int = 4
    bus_service_charge_paisa: int = 3000  # ৳30 per seat
    max_train_seats: int = 4
    train_service_charge_paisa: int = 2000  # ৳20 per seat
    flight_service_charge_paisa: int = 20000  # ৳200 per passenger with a seat (infants free)

    @property
    def is_prod(self) -> bool:
        return self.env == "prod"


@lru_cache
def get_settings() -> Settings:
    settings = Settings()
    if settings.is_prod:
        if settings.secret_key == "dev-only-change-me" or len(settings.secret_key) < 32:  # noqa: S105
            raise RuntimeError("JATRI_SECRET_KEY must be a long random value in production")
        if settings.dev_fixed_otp:
            raise RuntimeError("JATRI_DEV_FIXED_OTP must not be set in production")
        if not settings.data_key:
            raise RuntimeError("JATRI_DATA_KEY must be set in production")
        if not settings.redis_url:
            raise RuntimeError("JATRI_REDIS_URL must be set in production")
        if settings.payment_gateway == "mock":
            raise RuntimeError("The mock payment gateway is not allowed in production")
        if settings.payment_gateway == "sslcommerz" and settings.sslcommerz_sandbox:
            raise RuntimeError("Turn off JATRI_SSLCOMMERZ_SANDBOX in production")
    return settings
