"""E-tickets: a signed QR payload (so counters can verify it offline) and a Bangla/English PDF."""

import base64
import hashlib
import hmac
import io
from functools import lru_cache
from pathlib import Path

import segno
from fpdf import FPDF

from app.core.config import Settings
from app.models import Booking
from app.seed import load_locations

_FONTS = Path(__file__).resolve().parent.parent / "assets" / "fonts"
_BD_OFFSET_HOURS = 6


def qr_payload(booking: Booking, settings: Settings) -> str:
    """`JT1.<ref>.<booking id>.<signature>`. The signature stops anyone forging a ticket QR."""
    body = f"JT1.{booking.booking_ref}.{booking.id.hex}"
    sig = hmac.new(("ticket:" + settings.secret_key).encode(), body.encode(), hashlib.sha256).digest()[:12]
    return f"{body}.{base64.urlsafe_b64encode(sig).decode().rstrip('=')}"


def verify_qr(payload: str, settings: Settings) -> bool:
    body, _, sig = payload.rpartition(".")
    expected = hmac.new(("ticket:" + settings.secret_key).encode(), body.encode(), hashlib.sha256).digest()[:12]
    return hmac.compare_digest(sig, base64.urlsafe_b64encode(expected).decode().rstrip("="))


@lru_cache
def _place_names() -> dict[str, tuple[str, str]]:
    return {r["key"]: (r["city_en"], r["city_bn"]) for r in load_locations()}


def _aware(dt):
    from datetime import UTC

    return dt if dt.tzinfo else dt.replace(tzinfo=UTC)


def _bd(dt) -> str:
    from datetime import timedelta, timezone

    return _aware(dt).astimezone(timezone(timedelta(hours=_BD_OFFSET_HOURS))).strftime("%d %b %Y, %H:%M")


def _airport_time(location_id: str, dt) -> str:
    from app.providers.mock_flight import local_time

    return local_time(location_id, _aware(dt)).strftime("%d %b %Y, %H:%M")


_PAX_TYPE = {"adult": "Adult / প্রাপ্তবয়স্ক", "child": "Child / শিশু", "infant": "Infant / ইনফ্যান্ট"}


def render_pdf(booking: Booking, payload: str) -> bytes:
    """A5 e-ticket. Bangla needs HarfBuzz shaping (conjuncts), so text shaping is on."""
    seg = booking.segments[0]
    names = _place_names()
    from_en, from_bn = names.get(seg.from_id, (seg.from_id, seg.from_id))
    to_en, to_bn = names.get(seg.to_id, (seg.to_id, seg.to_id))

    pdf = FPDF(format=(148, 210))  # A5 in mm
    pdf.set_auto_page_break(auto=True, margin=12)
    pdf.add_page()
    pdf.add_font("Hind", "", str(_FONTS / "hindsiliguri_regular.ttf"))
    pdf.add_font("Hind", "B", str(_FONTS / "hindsiliguri_bold.ttf"))
    # Force Bengali shaping: HarfBuzz otherwise takes the script from the first letters of a line, and
    # lines that start in English ("A2 · রহিম") would get the pre-base vowel (ি) after the consonant.
    pdf.set_text_shaping(use_shaping_engine=True, script="beng", language="ben")
    w = pdf.epw

    # Header band
    pdf.set_fill_color(21, 24, 27)
    pdf.rect(0, 0, pdf.w, 26, style="F")
    pdf.set_text_color(255, 255, 255)
    pdf.set_font("Hind", "B", 18)
    pdf.set_xy(pdf.l_margin, 6)
    pdf.cell(w / 2, 10, "Jatri  যাত্রী")
    pdf.set_font("Hind", "", 10)
    pdf.cell(w / 2, 10, "E-ticket / ই-টিকিট", align="R")
    pdf.set_text_color(21, 24, 27)
    pdf.set_y(32)

    if booking.mode == "flight":
        # Title: the outbound journey (first flight's origin to the last flight of that journey).
        out = [x for x in booking.segments if (x.extra or {}).get("slice_index", 0) == 0]
        to_en, to_bn = names.get(out[-1].to_id, (out[-1].to_id, out[-1].to_id))
    pdf.set_font("Hind", "B", 16)
    pdf.multi_cell(w, 8, f"{from_en} - {to_en}", new_x="LMARGIN", new_y="NEXT")
    pdf.set_font("Hind", "", 12)
    pdf.multi_cell(w, 7, f"{from_bn} থেকে {to_bn}", new_x="LMARGIN", new_y="NEXT")
    pdf.ln(2)

    def row(label: str, value: str) -> None:
        pdf.set_font("Hind", "", 9)
        pdf.set_text_color(91, 102, 112)
        pdf.cell(w * 0.38, 6, label)
        pdf.set_font("Hind", "B", 11)
        pdf.set_text_color(21, 24, 27)
        pdf.multi_cell(w * 0.62, 6, value, new_x="LMARGIN", new_y="NEXT")

    if booking.mode == "flight":
        details = booking.details or {}
        rules = details.get("rules") or {}
        row("Booking ref / বুকিং", booking.booking_ref)
        row("Airline PNR / পিএনআর", booking.provider_booking_ref or "-")
        cabin = str(details.get("cabin", seg.travel_class)).replace("_", " ").title()
        row("Cabin / ক্লাস", f"{cabin} · {str(details.get('fare_brand', '')).title()}")
        row("Baggage / ব্যাগেজ", f"{rules.get('checked_baggage_kg', '-')} kg + {rules.get('cabin_baggage_kg', '-')} kg cabin")
        row("Total paid / মোট", f"BDT {booking.total_amount // 100:,}")
        pdf.ln(2)
        pdf.set_font("Hind", "B", 11)
        pdf.cell(w, 7, "Flights / ফ্লাইট (local times / স্থানীয় সময়)", new_x="LMARGIN", new_y="NEXT")
        for f in booking.segments:
            ex = f.extra or {}
            pdf.set_font("Hind", "B", 10)
            pdf.multi_cell(w, 5.5, f"{f.number}  ·  {ex.get('from_code', '')} - {ex.get('to_code', '')}  ·  {f.carrier}",
                           new_x="LMARGIN", new_y="NEXT")  # fmt: skip
            pdf.set_font("Hind", "", 9)
            pdf.multi_cell(w, 5, f"{_airport_time(f.from_id, f.depart_at)}  -  {_airport_time(f.to_id, f.arrive_at)}",
                           new_x="LMARGIN", new_y="NEXT")  # fmt: skip
        pdf.ln(2)
        pdf.set_font("Hind", "B", 11)
        pdf.cell(w, 7, "Passengers / যাত্রী", new_x="LMARGIN", new_y="NEXT")
        pdf.set_font("Hind", "", 10)
        for p in booking.passengers:
            pdf.multi_cell(w, 5.5, f"{p.full_name}  ·  {_PAX_TYPE.get(p.type, p.type)}", new_x="LMARGIN", new_y="NEXT")
            if p.ticket_number:
                pdf.set_text_color(91, 102, 112)
                pdf.multi_cell(w, 5, f"E-ticket / ই-টিকিট: {p.ticket_number}", new_x="LMARGIN", new_y="NEXT")
                pdf.set_text_color(21, 24, 27)
    else:
        bp = (seg.extra or {}).get("boarding_point") or {}
        dp = (seg.extra or {}).get("dropping_point") or {}
        row("Booking ref / বুকিং", booking.booking_ref)
        row("PNR", booking.provider_booking_ref or "-")
        if booking.mode == "train":
            klass = str(seg.travel_class).replace("_", " ").title()
            row("Train / ট্রেন", f"{seg.carrier} ({seg.number})")
            row("Class / শ্রেণি", f"{klass} · Coach {(seg.extra or {}).get('coach', '-')}")
        else:
            row("Operator / অপারেটর", f"{seg.carrier} ({seg.number})")
        row("Departure / যাত্রা", _bd(seg.depart_at))
        row("Arrival / পৌঁছানো", _bd(seg.arrive_at))
        if bp:
            row("Boarding / ওঠার স্থান", f"{bp.get('name_en', '')} · {bp.get('name_bn', '')}")
        if dp:
            row("Dropping / নামার স্থান", f"{dp.get('name_en', '')} · {dp.get('name_bn', '')}")
        row("Seats / সিট", ", ".join(seg.seat_numbers))
        row("Total paid / মোট", f"BDT {booking.total_amount // 100:,}")
        pdf.ln(2)

        pdf.set_font("Hind", "B", 11)
        pdf.cell(w, 7, "Passengers / যাত্রী", new_x="LMARGIN", new_y="NEXT")
        pdf.set_font("Hind", "", 11)
        for p in booking.passengers:
            pdf.multi_cell(w, 6, f"{p.seat_no or '-'}  ·  {p.full_name}", new_x="LMARGIN", new_y="NEXT")

    # QR code
    buf = io.BytesIO()
    segno.make(payload, error="m").save(buf, kind="png", scale=6, border=2)
    buf.seek(0)
    size = 38
    y = max(pdf.get_y() + 4, pdf.h - size - 22)
    pdf.image(buf, x=(pdf.w - size) / 2, y=y, w=size, h=size)
    pdf.set_y(y + size + 1)
    pdf.set_font("Hind", "", 8)
    pdf.set_text_color(91, 102, 112)
    hint = (
        "Show this at airline check-in with your ID. চেক-ইনের সময় পরিচয়পত্রসহ এটি দেখান।"
        if booking.mode == "flight"
        else "Show this QR and the passengers' NID to the ticket checker. টিকিট চেকারকে এই কিউআর ও এনআইডি দেখান।"
        if booking.mode == "train"
        else "Show this QR at the counter. কাউন্টারে এই কিউআর কোডটি দেখান।"
    )
    pdf.multi_cell(w, 4, hint, align="C", new_x="LMARGIN", new_y="NEXT")
    return bytes(pdf.output())
