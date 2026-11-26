import uuid
from datetime import datetime
from typing import Literal

from pydantic import BaseModel


class PaymentInitIn(BaseModel):
    booking_id: uuid.UUID
    method: Literal["bkash", "nagad", "rocket", "card"]


class PaymentInitOut(BaseModel):
    payment_id: uuid.UUID
    checkout_url: str
    amount: int
    status: str


class PaymentStatusOut(BaseModel):
    payment_id: uuid.UUID
    status: str
    failure_reason: str | None
    booking_id: uuid.UUID
    booking_status: str
    booking_ref: str


class TicketOut(BaseModel):
    qr_payload: str
    pdf_url: str
    issued_at: datetime
