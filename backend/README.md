# Jatri backend (FastAPI)

Phase 2: phone OTP login, JWT access + rotating refresh tokens, profile, in-app account deletion,
locations. Every response uses one envelope:

```json
{ "success": true, "data": { ... }, "error": null }
{ "success": false, "data": null, "error": { "code": "otp_invalid", "message_en": "...", "message_bn": "..." } }
```

## Run locally

With Docker:

```bash
cp .env.example .env        # dev values, OTP fixed to 123456
docker compose up --build   # API on http://localhost:8000, docs at /docs
```

Without Docker (needs PostgreSQL and [uv](https://docs.astral.sh/uv/)):

```bash
uv sync
cp .env.example .env
uv run alembic upgrade head
uv run uvicorn app.main:app --reload
```

## Test

```bash
uv run pytest                     # SQLite, no setup needed
JATRI_TEST_DATABASE_URL=postgresql+asyncpg://jatri:jatri@localhost:5432/jatri_test uv run pytest
uv run ruff check .
```

## Endpoints (`/api/v1`)

| Method | Path | Notes |
|---|---|---|
| POST | `/auth/otp/send` | `{phone}`. Accepts `01…`, `+880…` and Bangla digits. 60 s cooldown, 3 sends per 10 min |
| POST | `/auth/otp/verify` | `{phone, code}` → tokens + user + `is_new_user`. 5 wrong tries locks the code |
| POST | `/auth/refresh` | Rotates the refresh token. Re-using an old one logs that user out everywhere |
| POST | `/auth/logout` | Revokes the refresh token |
| GET / PATCH / DELETE | `/me` | Profile, edit (name, email, language), delete account |
| GET | `/locations?type=&q=` | Airports, bus cities, stations. Search matches English, Bangla and codes |

The full OpenAPI spec is at `/api/v1/openapi.json`; a copy is in [`docs/openapi.json`](../docs/openapi.json).

## Security notes

- OTPs and refresh tokens are stored only as HMAC-SHA256 hashes. The plain values never touch the database.
- Access tokens last 15 minutes, refresh tokens 30 days.
- `JATRI_ENV=prod` refuses to start with the dev secret key, a fixed OTP or the console SMS sender.
- Errors never include stack traces or personal data.

## What is still placeholder

- **SMS**: the `console` provider prints the SMS to the server log. A Bangladeshi SMS gateway
  adapter plugs into `app/services/sms.py`.
- **Rate limiting** is done in PostgreSQL for now. Redis takes over in Phase 3, along with the search
  cache and seat locks.
