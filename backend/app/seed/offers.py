"""Launch promo codes, inserted by migration 0006 (and by the tests)."""

from datetime import date


def seed_offers() -> list[dict]:
    """Launch offers (same codes the app showed as samples). Edit or add more in the admin later."""
    start, end = date(2026, 1, 1), date(2027, 12, 31)
    common = {"valid_from": start, "valid_to": end, "is_active": True, "used_count": 0, "usage_limit": None}
    return [
        {
            **common, "code": "JATRI150", "discount_type": "flat", "value": 15000, "max_discount": None, "min_amount": 50000,
            "modes": ["bus"], "methods": [], "first_booking_only": True, "per_user_limit": 1,
            "title_en": "৳150 off your first bus ticket", "title_bn": "প্রথম বাস টিকিটে ৳১৫০ ছাড়",
            "subtitle_en": "New users only · fares from ৳500", "subtitle_bn": "শুধু নতুন ব্যবহারকারীদের জন্য · ভাড়া ৳৫০০ থেকে",
        },
        {
            **common, "code": "FLY8", "discount_type": "percent", "value": 8, "max_discount": 80000, "min_amount": 0,
            "modes": ["flight"], "methods": ["bkash"], "first_booking_only": False, "per_user_limit": 3,
            "title_en": "8% off flights", "title_bn": "ফ্লাইটে ৮% ছাড়",
            "subtitle_en": "Pay with bKash · up to ৳800", "subtitle_bn": "বিকাশে পেমেন্টে · সর্বোচ্চ ৳৮০০",
        },
        {
            **common, "code": "RAIL50", "discount_type": "flat", "value": 5000, "max_discount": None, "min_amount": 30000,
            "modes": ["train"], "methods": ["nagad"], "first_booking_only": False, "per_user_limit": 5,
            "title_en": "৳50 off train tickets", "title_bn": "ট্রেন টিকিটে ৳৫০ ছাড়",
            "subtitle_en": "Pay with Nagad · fares from ৳300", "subtitle_bn": "নগদে পেমেন্টে · ভাড়া ৳৩০০ থেকে",
        },
        {
            **common, "code": "WELCOME100", "discount_type": "flat", "value": 10000, "max_discount": None, "min_amount": 100000,
            "modes": [], "methods": [], "first_booking_only": True, "per_user_limit": 1,
            "title_en": "৳100 off your first trip", "title_bn": "প্রথম যাত্রায় ৳১০০ ছাড়",
            "subtitle_en": "Flight, bus or train · from ৳1,000", "subtitle_bn": "ফ্লাইট, বাস বা ট্রেন · ৳১,০০০ থেকে",
        },
    ]  # fmt: skip
