#!/usr/bin/env python3
"""Turn an estimated meal into the two things GetFit can accept.

Reads a meal as JSON on stdin, checks it, and writes:

  * ``nutrition/inbox/<id>.json`` — the file to commit to the inbox branch
  * a ``getfit://`` link on stdout — the offline route, for a phone in hand

The estimating is the model's job and the arithmetic is this script's, which is
the split that matters: base64 worked out in your head is wrong roughly every
time, and a meal whose numbers do not add up is worse than no meal at all
because it is indistinguishable from one that does.

Usage:
    echo '<json>' | publish_meal.py [--repo-root DIR] [--dev] [--no-write]
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import re
import sys
import time
import unicodedata

SCHEMA_VERSION = 1
LINK_VERSION = 1

# Matches MealLink.MAX_PLAUSIBLE_CALORIES on the app side. Both exist to catch a
# decimal point in the wrong place, not to police lunch.
MAX_CALORIES = 20_000

# Matches MealEntry.TOTALS_TOLERANCE. Ten per cent, because these are estimates
# from a photograph.
TOTALS_TOLERANCE = 0.10

CONFIDENCES = {"LOW", "MEDIUM", "HIGH"}

NUTRITION_FIELDS = (
    "calories",
    "proteinG",
    "carbsG",
    "fatG",
    "fibreG",
    "sugarG",
    "sodiumMg",
)


class MealError(Exception):
    """A meal that should be fixed before it is published."""


def slugify(text: str) -> str:
    """A short, filename-safe token from a label."""
    normalised = unicodedata.normalize("NFKD", text)
    ascii_only = normalised.encode("ascii", "ignore").decode("ascii")
    slug = re.sub(r"[^a-zA-Z0-9]+", "-", ascii_only).strip("-").lower()
    return (slug or "meal")[:40]


def normalise_nutrition(raw: dict, where: str) -> dict:
    """Fills in the absent fields with zero and rejects the impossible ones."""
    if not isinstance(raw, dict):
        raise MealError(f"{where}: nutrition must be an object")

    out = {}
    for field in NUTRITION_FIELDS:
        value = raw.get(field, 0)
        if value is None:
            value = 0
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            raise MealError(f"{where}: {field} must be a number, got {value!r}")
        if value < 0:
            raise MealError(f"{where}: {field} cannot be negative ({value})")
        out[field] = float(value)

    unknown = set(raw) - set(NUTRITION_FIELDS)
    if unknown:
        raise MealError(f"{where}: unknown nutrition fields {sorted(unknown)}")

    return out


def normalise(raw: dict) -> dict:
    """Checks a meal and fills in everything the app expects to be present."""
    if not isinstance(raw, dict):
        raise MealError("the meal must be a JSON object")

    label = str(raw.get("label", "")).strip()
    if not label:
        raise MealError("the meal needs a label — a short name a person would recognise")

    captured_at = raw.get("capturedAt") or int(time.time())
    if not isinstance(captured_at, int) or isinstance(captured_at, bool):
        raise MealError("capturedAt must be epoch seconds, as an integer")
    if captured_at <= 0:
        raise MealError("capturedAt must be a real moment")

    totals = normalise_nutrition(raw.get("totals", {}), "totals")
    if totals["calories"] <= 0:
        raise MealError("a meal with no calories is not an estimate, it is a gap")
    if totals["calories"] > MAX_CALORIES:
        raise MealError(
            f"{totals['calories']:.0f} kcal is not a meal — check the decimal point"
        )

    items = []
    for index, item in enumerate(raw.get("items", []) or []):
        if not isinstance(item, dict):
            raise MealError(f"items[{index}] must be an object")
        name = str(item.get("name", "")).strip()
        if not name:
            raise MealError(f"items[{index}] needs a name")
        grams = item.get("grams")
        if grams is not None:
            if isinstance(grams, bool) or not isinstance(grams, (int, float)) or grams < 0:
                raise MealError(f"items[{index}].grams must be a non-negative number")
            grams = float(grams)
        items.append(
            {
                "name": name,
                "quantity": str(item.get("quantity", "")).strip(),
                "grams": grams,
                "nutrition": normalise_nutrition(item.get("nutrition", {}), f"items[{index}]"),
            }
        )

    confidence = str(raw.get("confidence", "MEDIUM")).upper()
    if confidence not in CONFIDENCES:
        raise MealError(f"confidence must be one of {sorted(CONFIDENCES)}")

    # The id is the whole of the app's defence against filing one lunch twice,
    # so it is derived from the meal rather than randomised: publishing the same
    # meal again overwrites one inbox file instead of adding a second.
    meal_id = str(raw.get("id", "")).strip() or f"{captured_at}-{slugify(label)}"
    if not re.fullmatch(r"[A-Za-z0-9._-]{1,80}", meal_id):
        raise MealError(
            f"id {meal_id!r} must be 1-80 characters of letters, digits, dot, dash or underscore "
            "— it becomes a filename"
        )

    return {
        "id": meal_id,
        "capturedAt": captured_at,
        "label": label,
        "items": items,
        "totals": totals,
        "confidence": confidence,
        "notes": str(raw.get("notes", "")).strip(),
        "source": "skill",
        "schema": SCHEMA_VERSION,
    }


def warnings_for(meal: dict) -> list[str]:
    """Things worth saying out loud that are not reasons to refuse the meal."""
    notes = []
    totals = meal["totals"]

    if meal["items"]:
        implied = sum(item["nutrition"]["calories"] for item in meal["items"])
        stated = totals["calories"]
        if stated > 0 and abs(implied - stated) / stated > TOTALS_TOLERANCE:
            notes.append(
                f"the items add up to {implied:.0f} kcal but the total says {stated:.0f} — "
                "the app will flag this to the reader"
            )
    else:
        notes.append("no items, so nothing shows when the meal is expanded")

    macro_kcal = totals["proteinG"] * 4 + totals["carbsG"] * 4 + totals["fatG"] * 9
    stated = totals["calories"]
    if stated > 0 and abs(macro_kcal - stated) / stated > 0.20:
        notes.append(
            f"the macros imply {macro_kcal:.0f} kcal but the total says {stated:.0f} — "
            "they were probably not estimated from the same portions"
        )

    if not meal["notes"]:
        notes.append("no notes, so whatever the estimate assumed is not written down")

    return notes


def deep_link(meal: dict, dev: bool) -> str:
    payload = json.dumps(meal, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    encoded = base64.urlsafe_b64encode(payload).decode("ascii").rstrip("=")
    host = "meal-dev" if dev else "meal"
    return f"getfit://{host}?v={LINK_VERSION}&d={encoded}"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root", default=".", help="repository root (default: cwd)")
    parser.add_argument("--dev", action="store_true", help="target a GetFit DEV install")
    parser.add_argument(
        "--no-write",
        action="store_true",
        help="print the link and the JSON without writing the inbox file",
    )
    args = parser.parse_args()

    try:
        raw = json.load(sys.stdin)
    except json.JSONDecodeError as exc:
        print(f"error: stdin is not valid JSON — {exc}", file=sys.stderr)
        return 2

    try:
        meal = normalise(raw)
    except MealError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1

    body = json.dumps(meal, indent=2, ensure_ascii=False) + "\n"

    if not args.no_write:
        inbox = os.path.join(args.repo_root, "nutrition", "inbox")
        os.makedirs(inbox, exist_ok=True)
        path = os.path.join(inbox, f"{meal['id']}.json")
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(body)
        print(f"wrote {path}", file=sys.stderr)

    for note in warnings_for(meal):
        print(f"note: {note}", file=sys.stderr)

    print(f"{meal['totals']['calories']:.0f} kcal — {meal['label']}", file=sys.stderr)
    print(deep_link(meal, args.dev))
    return 0


if __name__ == "__main__":
    sys.exit(main())
