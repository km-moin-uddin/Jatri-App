import re

_BN = str.maketrans("0123456789", "০১২৩৪৫৬৭৮৯")
_TOKEN = re.compile(r"[A-Za-z0-9]*\d[A-Za-z0-9]*")


def bn_digits(text: str) -> str:
    """Bangla text shows Bangla digits ("১০% ফি", "৳ ১,২৬০"), but codes with letters stay as they
    are printed on the ticket (booking ref "JTM96Y", flight "S1")."""
    return _TOKEN.sub(lambda m: m.group() if re.search(r"[A-Za-z]", m.group()) else m.group().translate(_BN), text)
