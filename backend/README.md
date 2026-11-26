# Jatri backend (FastAPI)

Starts in **Phase 2** (phone OTP auth). Planned layout:

```
backend/
├── app/
│   ├── api/v1/          # routers: auth, locations, flights, buses, trains, bookings, payments …
│   ├── core/            # settings, security (JWT, encryption), logging
│   ├── db/              # SQLAlchemy models, session; Alembic migrations in ../alembic
│   ├── providers/       # FlightProvider / BusProvider / TrainProvider adapters (mock first)
│   ├── payments/        # PaymentGateway adapters (SSLCommerz / bKash / Nagad sandbox)
│   └── workers/         # Celery/ARQ jobs: ticket issuing, reminders, refunds
├── alembic/
├── tests/
├── docker-compose.yml   # api + postgres + redis
└── .env.example
```

Every response uses one envelope: `{ "success": bool, "data": ..., "error": { "code", "message_en", "message_bn" } }`.
The Android app already has a matching `ApiEnvelope` in `android/core/network`.
