from __future__ import annotations

import mimetypes
import re
import zipfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any
from xml.etree import ElementTree


@dataclass(slots=True)
class ExtractedContent:
    text: str = ""
    labels: list[str] = field(default_factory=list)
    attributes: dict[str, Any] = field(default_factory=dict)


def detect_media_type(path: Path, declared: str | None = None) -> str:
    return declared or mimetypes.guess_type(path.name)[0] or "application/octet-stream"


def _extract_plain(path: Path) -> ExtractedContent:
    return ExtractedContent(text=path.read_text(encoding="utf-8", errors="replace"))


def _extract_pdf(path: Path) -> ExtractedContent:
    try:
        from pypdf import PdfReader

        reader = PdfReader(str(path))
        text = "\n".join(page.extract_text() or "" for page in reader.pages)
        return ExtractedContent(text=text, attributes={"page_count": len(reader.pages), "extractor": "pypdf"})
    except ImportError:
        # Best-effort extraction for simple text PDFs; install the documents extra for reliability.
        raw = path.read_bytes()
        chunks = re.findall(rb"[\x20-\x7e]{5,}", raw)
        return ExtractedContent(text=" ".join(x.decode("latin-1") for x in chunks), attributes={"extractor": "pdf-fallback"})


def _extract_docx(path: Path) -> ExtractedContent:
    with zipfile.ZipFile(path) as archive:
        xml = archive.read("word/document.xml")
    root = ElementTree.fromstring(xml)
    text = "\n".join("".join(node.itertext()) for node in root.iter() if node.tag.endswith("}p"))
    return ExtractedContent(text=text, attributes={"extractor": "docx-xml"})


def _extract_image(path: Path) -> ExtractedContent:
    try:
        from PIL import Image, ImageEnhance, ImageFilter, ImageOps
        import pytesseract
    except ImportError:
        return ExtractedContent(attributes={"extractor": "unavailable", "warning": "Install recallx-engine[images] and Tesseract OCR"})

    try:
        with Image.open(path) as original:
            image = ImageOps.exif_transpose(original).convert("L")
            image.thumbnail((2400, 2400))
            image = ImageEnhance.Contrast(image).enhance(1.8).filter(ImageFilter.SHARPEN)
            text = pytesseract.image_to_string(image, config="--oem 3 --psm 6")
            width, height = original.size
    except (OSError, RuntimeError) as error:
        return ExtractedContent(attributes={"extractor": "ocr-error", "warning": str(error)})
    labels = classify_image_context(text, path.name)
    return ExtractedContent(text=text, labels=labels, attributes={"width": width, "height": height, "extractor": "tesseract"})


def classify_image_context(text: str, name: str) -> list[str]:
    haystack = f"{name} {text}".casefold()
    rules = {
        "screenshot": ("screenshot", "status bar", "battery"),
        "shopping": ("add to cart", "buy now", "delivery", "₹"),
        "travel": ("hotel", "flight", "booking", "check-in", "destination"),
        "food": ("restaurant", "menu", "swiggy", "zomato", "dish"),
        "map": ("directions", "km", "maps", "route"),
        "document": ("invoice", "receipt", "statement", "page"),
        "conversation": ("whatsapp", "message", "online", "typing"),
    }
    return [label for label, terms in rules.items() if any(term in haystack for term in terms)]


def _extract_audio(path: Path) -> ExtractedContent:
    try:
        from faster_whisper import WhisperModel
    except ImportError:
        return ExtractedContent(labels=["voice_note"], attributes={"extractor": "unavailable", "warning": "Install recallx-engine[audio]"})
    model = WhisperModel("tiny", device="cpu", compute_type="int8")
    segments, info = model.transcribe(str(path), vad_filter=True)
    text = " ".join(segment.text.strip() for segment in segments)
    return ExtractedContent(text=text, labels=["voice_note"], attributes={"language": info.language, "extractor": "faster-whisper-tiny"})


def extract(path: str | Path, media_type: str | None = None) -> ExtractedContent:
    source = Path(path)
    kind = detect_media_type(source, media_type)
    suffix = source.suffix.casefold()
    if kind.startswith("image/"):
        return _extract_image(source)
    if kind.startswith("audio/"):
        return _extract_audio(source)
    if kind == "application/pdf" or suffix == ".pdf":
        return _extract_pdf(source)
    if suffix == ".docx":
        return _extract_docx(source)
    if kind.startswith("text/") or suffix in {".md", ".json", ".csv", ".log"}:
        return _extract_plain(source)
    return ExtractedContent(attributes={"extractor": "unsupported", "warning": f"Unsupported media type: {kind}"})
