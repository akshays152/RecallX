from __future__ import annotations

import mimetypes
import os
import zipfile
from functools import lru_cache
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
    if declared and declared != "application/octet-stream":
        return declared
    return mimetypes.guess_type(path.name)[0] or declared or "application/octet-stream"


def _extract_plain(path: Path) -> ExtractedContent:
    return ExtractedContent(text=path.read_text(encoding="utf-8", errors="replace"))


def _extract_pdf(path: Path) -> ExtractedContent:
    try:
        from pypdf import PdfReader

        reader = PdfReader(str(path))
        pages = [page.extract_text() or "" for page in reader.pages]
        scanned = [index for index, text in enumerate(pages) if not text.strip()]
        if scanned:
            try:
                import pymupdf
                from PIL import Image
                document = pymupdf.open(str(path))
                for index in scanned:
                    pix = document[index].get_pixmap(matrix=pymupdf.Matrix(2, 2), alpha=False)
                    image = Image.frombytes("RGB", (pix.width, pix.height), pix.samples)
                    pages[index], _ = _ocr_image(image)
                document.close()
            except ImportError:
                pass
        return ExtractedContent(text="\n".join(pages), attributes={"page_count": len(reader.pages), "extractor": "pypdf+ocr" if scanned else "pypdf", "scanned_pages": len(scanned)})
    except ImportError:
        return ExtractedContent(attributes={"extractor": "unavailable", "warning": "Install recallx-engine[documents]"})


def _extract_docx(path: Path) -> ExtractedContent:
    with zipfile.ZipFile(path) as archive:
        xml = archive.read("word/document.xml")
    root = ElementTree.fromstring(xml)
    text = "\n".join("".join(node.itertext()) for node in root.iter() if node.tag.endswith("}p"))
    return ExtractedContent(text=text, attributes={"extractor": "docx-xml"})


def _extract_image(path: Path) -> ExtractedContent:
    try:
        from PIL import Image, ImageOps
    except ImportError:
        return ExtractedContent(attributes={"extractor": "unavailable", "warning": "Install recallx-engine[images]"})

    try:
        with Image.open(path) as original:
            width, height = original.size
            image = ImageOps.exif_transpose(original).convert("RGB")
            image.thumbnail((2400, 2400))
            text, extractor = _ocr_image(image)
    except (OSError, RuntimeError) as error:
        return ExtractedContent(attributes={"extractor": "ocr-error", "warning": str(error)})
    labels = classify_image_context(text, path.name)
    return ExtractedContent(text=text, labels=labels, attributes={"width": width, "height": height, "extractor": extractor})


def _ocr_image(image) -> tuple[str, str]:
    try:
        from numpy import asarray
        result, _ = _rapid_ocr()(asarray(image))
        return "\n".join(item[1] for item in (result or [])), "rapidocr-onnx"
    except ImportError:
        try:
            import pytesseract
            return pytesseract.image_to_string(image, config="--oem 3 --psm 6"), "tesseract"
        except ImportError:
            raise ValueError("Install recallx-engine[images] for OCR")


@lru_cache(maxsize=1)
def _rapid_ocr():
    from rapidocr_onnxruntime import RapidOCR
    return RapidOCR()


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
    try:
        model = _whisper_model()
    except (OSError, RuntimeError) as error:
        return ExtractedContent(labels=["voice_note"], attributes={"extractor": "unavailable", "warning": f"Whisper model not cached: {error}"})
    segments, info = model.transcribe(str(path), vad_filter=True)
    text = " ".join(segment.text.strip() for segment in segments)
    return ExtractedContent(text=text, labels=["voice_note"], attributes={"language": info.language, "extractor": "faster-whisper-tiny"})


@lru_cache(maxsize=1)
def _whisper_model():
    from faster_whisper import WhisperModel
    return WhisperModel(
        "tiny", device="cpu", compute_type="int8",
        local_files_only=os.environ.get("RECALLX_ALLOW_MODEL_DOWNLOAD", "0") != "1",
    )


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
