"""Seed data. locations.json mirrors android/core/data/.../SeedLocations.kt (same ids)."""

import json
from pathlib import Path


def load_locations() -> list[dict]:
    return json.loads((Path(__file__).parent / "locations.json").read_text(encoding="utf-8"))
