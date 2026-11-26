import html
import uuid
from typing import Annotated
from urllib.parse import urlencode

from fastapi import APIRouter, Depends, Form, Header, Request
from fastapi.responses import HTMLResponse, RedirectResponse, Response

from app.api.deps import AppSettings, CurrentUser, DbSession
from app.api.v1.buses import StoreDep
from app.core.envelope import AppError, ok
from app.models import Booking, Payment
from app.payments import get_gateway
from app.payments.mock import MockGateway
from app.providers.registry import ProvidersDep
from app.schemas.common import Envelope
from app.schemas.payment import PaymentInitIn, PaymentInitOut, PaymentStatusOut
from app.services import payment as service
from app.services.ticket import render_pdf

router = APIRouter(tags=["payments"])


def gateway_dep(settings: AppSettings):
    return get_gateway(settings)


Gateway = Annotated[object, Depends(gateway_dep)]


@router.post("/payments/init", response_model=Envelope[PaymentInitOut])
async def init(
    body: PaymentInitIn,
    user: CurrentUser,
    db: DbSession,
    gateway: Gateway,
    provider: ProvidersDep,
    store: StoreDep,
    idempotency_key: Annotated[str | None, Header(max_length=64)] = None,
):
    """Starts a payment for a held booking and returns the gateway page to open in a browser tab."""
    payment, url = await service.init_payment(db, gateway, provider, store, user, body.booking_id, body.method, idempotency_key)
    return ok(PaymentInitOut(payment_id=payment.id, checkout_url=url, amount=payment.amount, status=payment.status))


@router.post("/payments/callback/{gateway_name}", response_model=Envelope[PaymentStatusOut])
async def callback(
    gateway_name: str,
    request: Request,
    db: DbSession,
    gateway: Gateway,
    provider: ProvidersDep,
    store: StoreDep,
    settings: AppSettings,
):
    """Server-to-server notification from the gateway (IPN). This, not the app, decides if a payment succeeded."""
    if gateway_name != gateway.name:
        raise AppError(404, "not_found", "Not found.", "খুঁজে পাওয়া যায়নি।")
    form = dict(await request.form())
    payment = await service.process_callback(db, gateway, provider, store, settings, form)
    booking = await db.get(Booking, payment.booking_id)
    return ok(_status_out(payment, booking))


@router.get("/payments/{payment_id}/status", response_model=Envelope[PaymentStatusOut])
async def status(payment_id: uuid.UUID, user: CurrentUser, db: DbSession, provider: ProvidersDep, store: StoreDep):
    payment, booking = await service.payment_status(db, provider, store, user, payment_id)
    return ok(_status_out(payment, booking))


@router.api_route("/payments/return/{gateway_name}", methods=["GET", "POST"], include_in_schema=False)
async def browser_return(gateway_name: str, request: Request, settings: AppSettings):
    """Where the gateway sends the *browser* afterwards. Only hands control back to the app; the
    result itself comes from the callback."""
    params = dict(request.query_params)
    if request.method == "POST":
        params |= {k: str(v) for k, v in (await request.form()).items()}
    payment_id = params.get("tran_id") or params.get("payment_id", "")
    return RedirectResponse(f"{settings.app_return_url}?{urlencode({'payment_id': payment_id})}", status_code=303)


@router.get("/tickets/{booking_id}/pdf", response_class=Response, responses={200: {"content": {"application/pdf": {}}}})
async def ticket_pdf(booking_id: uuid.UUID, user: CurrentUser, db: DbSession, provider: ProvidersDep, store: StoreDep):
    booking, ticket = await service.ticket_for(db, provider, store, user, booking_id)
    pdf = render_pdf(booking, ticket.qr_payload)
    return Response(
        pdf,
        media_type="application/pdf",
        headers={
            "Content-Disposition": f'inline; filename="Jatri-{booking.booking_ref}.pdf"',
            "Cache-Control": "private, max-age=86400",
        },
    )


def _status_out(payment: Payment, booking: Booking) -> PaymentStatusOut:
    return PaymentStatusOut(
        payment_id=payment.id,
        status=payment.status,
        failure_reason=payment.failure_reason,
        booking_id=booking.id,
        booking_status=booking.status,
        booking_ref=booking.booking_ref,
    )


# ---- SANDBOX ONLY: the mock gateway's hosted checkout page --------------------------------------

_PAGE = """<!doctype html><html lang="bn"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1"><title>Demo payment</title>
<style>
 body{{margin:0;font-family:system-ui,sans-serif;background:#ECEDEF;color:#15181B;display:flex;min-height:100vh;align-items:center;justify-content:center}}
 .card{{background:#F7F8F9;border-radius:24px;padding:24px;max-width:360px;width:calc(100% - 32px);box-shadow:0 2px 16px #0001}}
 .tag{{display:inline-block;background:#FEF3C7;color:#B45309;border-radius:99px;padding:4px 12px;font-size:13px}}
 h1{{font-size:22px;margin:16px 0 4px}} .amt{{font-size:32px;font-weight:800;margin:8px 0 16px}}
 .muted{{color:#5B6670;font-size:14px}}
 button{{width:100%;height:52px;border:0;border-radius:99px;font-size:16px;font-weight:600;
   margin-top:12px;cursor:pointer}}
 .pay{{background:#FF7A45;color:#15181B}} .fail{{background:#fff;color:#15181B;border:1px solid #C3C8CE}}
</style></head><body><div class="card">
<span class="tag">SANDBOX · ডেমো পেমেন্ট</span>
<h1>{method}</h1><div class="muted">Jatri · {ref}</div>
<div class="amt">৳ {amount}</div>
<div class="muted">No real money moves on this page. এই পেজে কোনো আসল টাকা কাটা হয় না।</div>
<form method="post" action="{action}"><input type="hidden" name="result" value="success">
<button class="pay" type="submit">Pay / পেমেন্ট করুন</button></form>
<form method="post" action="{action}"><input type="hidden" name="result" value="fail">
<button class="fail" type="submit">Fail / ব্যর্থ করুন</button></form>
</div></body></html>"""


def _require_mock(settings) -> None:
    if settings.is_prod or settings.payment_gateway != "mock":
        raise AppError(404, "not_found", "Not found.", "খুঁজে পাওয়া যায়নি।")


@router.get("/payments/mock/checkout/{payment_id}", response_class=HTMLResponse, include_in_schema=False)
async def mock_checkout(payment_id: uuid.UUID, db: DbSession, settings: AppSettings):
    _require_mock(settings)
    payment = await db.get(Payment, payment_id)
    if payment is None:
        raise AppError(404, "payment_not_found", "Payment not found.", "পেমেন্ট পাওয়া যায়নি।")
    booking = await db.get(Booking, payment.booking_id)
    return _PAGE.format(
        method=html.escape(payment.method.upper()),
        ref=html.escape(booking.booking_ref),
        amount=f"{payment.amount // 100:,}",
        action=f"{settings.public_base_url.rstrip('/')}/api/v1/payments/mock/complete/{payment.id}",
    )


@router.post("/payments/mock/complete/{payment_id}", include_in_schema=False)
async def mock_complete(
    payment_id: uuid.UUID,
    result: Annotated[str, Form()],
    db: DbSession,
    provider: ProvidersDep,
    store: StoreDep,
    settings: AppSettings,
):
    _require_mock(settings)
    payment = await db.get(Payment, payment_id)
    if payment is None:
        raise AppError(404, "payment_not_found", "Payment not found.", "পেমেন্ট পাওয়া যায়নি।")
    gateway = MockGateway(settings)
    # Same path a real gateway's IPN takes: a signed form, verified before anything changes.
    form = gateway.completion_form(payment.id, result == "success", payment.amount)
    await service.process_callback(db, gateway, provider, store, settings, form)
    return RedirectResponse(f"{settings.app_return_url}?{urlencode({'payment_id': str(payment.id)})}", status_code=303)
