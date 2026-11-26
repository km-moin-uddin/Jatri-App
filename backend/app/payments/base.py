"""PaymentGateway adapter. The app never talks to a gateway directly and we never trust the app's
"success": only a verified server-to-server callback marks a payment as paid."""

import uuid
from dataclasses import dataclass, field
from typing import Protocol


@dataclass(frozen=True)
class CheckoutRequest:
    payment_id: uuid.UUID
    amount_paisa: int
    method: str  # bkash | nagad | rocket | card
    booking_ref: str
    customer_name: str
    customer_phone: str
    customer_email: str | None
    product_name: str
    item_count: int


@dataclass(frozen=True)
class CheckoutSession:
    checkout_url: str
    session_id: str | None = None


@dataclass(frozen=True)
class VerifiedCallback:
    """A callback whose authenticity the gateway adapter has already checked."""

    payment_id: uuid.UUID
    success: bool
    amount_paisa: int | None
    gateway_txn_id: str | None
    raw: dict = field(default_factory=dict)


class GatewayError(Exception):
    pass


class InvalidCallback(Exception):
    """Signature or validation failed: ignore the callback (and log it)."""


class PaymentGateway(Protocol):
    name: str

    async def create_session(self, req: CheckoutRequest) -> CheckoutSession: ...

    async def verify_callback(self, form: dict) -> VerifiedCallback: ...
