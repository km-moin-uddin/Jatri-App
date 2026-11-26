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
    return settings
