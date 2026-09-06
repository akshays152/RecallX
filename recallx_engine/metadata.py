from __future__ import annotations

import re
from collections import defaultdict
from datetime import datetime
from typing import Any


_MONTH = r"Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?"
_PRICE_RE = re.compile(r"(?<!\w)(?:₹|Rs\.?|INR|\$|USD|€|EUR|£|GBP)\s?\d[\d,]*(?:\.\d{1,2})?", re.I)
_DATE_PATTERNS = [
    re.compile(r"\b\d{1,2}[/-]\d{1,2}[/-](?:\d{2}|\d{4})\b"),
    re.compile(rf"\b(?:\d{{1,2}}\s+(?:{_MONTH})|(?:{_MONTH})\s+\d{{1,2}})(?:,?\s+\d{{4}})?\b", re.I),
    re.compile(r"\b20\d{2}-\d{2}-\d{2}\b"),
]
_PHONE_RE = re.compile(r"(?<!\d)(?:\+?91[-\s]?)?[6-9]\d{9}(?!\d)")
_EMAIL_RE = re.compile(r"\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b", re.I)
_URL_RE = re.compile(r"https?://[^\s<>]+", re.I)
_TIME_RE = re.compile(r"\b(?:[01]?\d|2[0-3]):[0-5]\d(?:\s?[AP]M)?\b", re.I)
_LOCATION_RE = re.compile(r"\b(?:at|in|near|from|to)\s+([A-Z][\w.-]+(?:\s+[A-Z][\w.-]+){0,3})")
_PERSON_RE = re.compile(r"\b(?:from|with|contact|name|by)[:\s]+([A-Z][a-z]+(?:\s+[A-Z][a-z]+){1,2})")
_PRODUCT_RE = re.compile(r"\b(?:[A-Z][A-Za-z]+[ -])?(?:[A-Z]{1,5}-?)?[A-Z]*\d{1,4}[A-Z0-9-]*\b")


def _unique(values: list[str]) -> list[str]:
    return list(dict.fromkeys(v.strip(" .,;:") for v in values if v.strip(" .,;:")))


def infer_document_type(text: str, media_type: str, name: str) -> str:
    haystack = f"{name} {text[:2000]}".casefold()
    rules = {
        "receipt": ("receipt", "subtotal", "amount paid", "payment successful"),
        "invoice": ("invoice", "gstin", "bill to", "invoice number"),
        "ticket": ("boarding pass", "pnr", "ticket", "departure", "booking id"),
        "menu": ("menu", "appetizer", "main course", "add to cart"),
        "identity_document": ("passport", "aadhaar", "driving licence", "date of birth"),
        "conversation": ("whatsapp", "telegram", "message", "sent a photo"),
    }
    for kind, needles in rules.items():
        if sum(needle in haystack for needle in needles) >= (1 if kind == "identity_document" else 2):
            return kind
    if media_type == "application/pdf":
        return "pdf_document"
    if media_type.startswith("image/"):
        return "screenshot" if "screenshot" in name.casefold() else "photo"
    if media_type.startswith("audio/"):
        return "voice_note"
    return "text"


def extract_structured(text: str, media_type: str, name: str = "") -> dict[str, Any]:
    buckets: dict[str, list[str]] = defaultdict(list)
    buckets["prices"] = _PRICE_RE.findall(text)
    buckets["dates"] = [match.group(0) for pattern in _DATE_PATTERNS for match in pattern.finditer(text)]
    buckets["times"] = _TIME_RE.findall(text)
    buckets["phones"] = _PHONE_RE.findall(text)
    buckets["emails"] = _EMAIL_RE.findall(text)
    buckets["urls"] = _URL_RE.findall(text)
    buckets["locations"] = _LOCATION_RE.findall(text)
    buckets["people"] = _PERSON_RE.findall(text)
    buckets["products"] = _PRODUCT_RE.findall(text)
    result: dict[str, Any] = {key: _unique(values) for key, values in buckets.items() if values}
    result["document_type"] = infer_document_type(text, media_type, name)
    return result


def parse_date_filter(value: str | None) -> datetime | None:
    if not value:
        return None
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        return None
