# Jatri backend (FastAPI)

Phase 2: phone OTP login, JWT access + rotating refresh tokens, profile, in-app account deletion,
locations. Phase 3: bus search, seat maps and booking holds through a provider adapter. Every
response uses one envelope:

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
| POST | `/buses/search` | `{from_id, to_id, date, coach_type?}`. Cached 5 min in Redis |
| GET | `/buses/{trip_id}/seats` | Seat map; seats held by others show as `held` |
| POST | `/bookings` | Holds seats for 10 min (`held`). Send `Idempotency-Key`; pass `expected_total` to detect price changes |
| GET | `/bookings`, `/bookings/{id}` | Your bookings only (others get 404). Expired holds turn `cancelled` |
| POST | `/bookings/{id}/cancel` | Releases an unpaid hold (paid cancellations + refunds: Phase 7) |

The full OpenAPI spec is at `/api/v1/openapi.json`; a copy is in [`docs/openapi.json`](../docs/openapi.json).

## Security notes

- OTPs and refresh tokens are stored only as HMAC-SHA256 hashes. The plain values never touch the database.
- Access tokens last 15 minutes, refresh tokens 30 days.
- `JATRI_ENV=prod` refuses to start with the dev secret key, a fixed OTP or the console SMS sender.
- Errors never include stack traces or personal data.
- NID / passport numbers are encrypted at rest (`JATRI_DATA_KEY`, Fernet) and only shown masked.
- Seat locks are atomic in Redis (Lua script): a booking gets all its seats or none.

## What is still placeholder

- **SMS**: the `console` provider prints the SMS to the server log. A Bangladeshi SMS gateway
  adapter plugs into `app/services/sms.py`.
- **Bus data** comes from `MockBusProvider` (placeholder operators). An authorised bus API
  implements the same `BusProvider` interface.
- **Hold expiry** happens when a booking or seat map is read, and the Redis lock TTL frees seats on its
  own. A periodic cleanup job arrives with the background worker in Phase 4.
- **OTP rate limiting** still uses PostgreSQL. It can move to Redis too.
