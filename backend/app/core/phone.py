"""Bangladeshi mobile numbers. Stored in one canonical form: 01XXXXXXXXX (11 digits)."""

import re

_BANGLA_DIGITS = str.maketrans("০১২৩৪৫৬৭৮৯", "0123456789")
_VALID = re.compile(r"^01[3-9]\d{8}$")


def normalize_bd_phone(raw: str) -> str | None:
    """Accepts 01XXXXXXXXX, +8801XXXXXXXXX, 8801XXXXXXXXX, spaces/dashes and Bangla digits."""
    digits = re.sub(r"\D", "", raw.translate(_BANGLA_DIGITS))
    if digits.startswith("880"):
        digits = digits[2:]
    return digits if _VALID.match(digits) else None
