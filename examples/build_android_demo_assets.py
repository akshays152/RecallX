"""Prepare bundled demo files and extracted content; no runtime OCR is implied.

Run with the project's image/document extras installed. Original sample files
are copied unchanged, and the app searches their pre-extracted text locally.
"""
from pathlib import Path
import json
import shutil

from PIL import Image, ImageOps
import pymupdf

from recallx_engine.extractors import extract, classify_image_context, detect_media_type
from recallx_engine.metadata import extract_structured


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "demo" / "sample_memories"
DEST = ROOT / "android" / "app" / "src" / "main" / "assets" / "demo"


def main():
    originals = DEST / "originals"
    previews = DEST / "previews"
    originals.mkdir(parents=True, exist_ok=True)
    previews.mkdir(parents=True, exist_ok=True)
    names = ["hotel_booking.png", "flight_ticket.png", "restaurant_menu.png",
             "shopping_receipt.png", "event_poster.png", "travel_note.png",
             "equipment_invoice.pdf", "goa_travel_itinerary.pdf", "design_review_notes.pdf"]
    memories = []
    for name in names:
        source = SOURCE / name
        media_type = detect_media_type(source)
        extracted = extract(source, media_type)
        assert extracted.text.strip(), f"No extracted content for {name}"
        shutil.copyfile(source, originals / name)
        if source.suffix == ".pdf":
            with pymupdf.open(source) as document:
                pix = document[0].get_pixmap(matrix=pymupdf.Matrix(1.5, 1.5), alpha=False)
                image = Image.frombytes("RGB", (pix.width, pix.height), pix.samples)
        else:
            with Image.open(source) as original:
                image = ImageOps.exif_transpose(original).convert("RGB")
        image.thumbnail((640, 640))
        preview_name = source.stem + ".jpg"
        image.save(previews / preview_name, "JPEG", quality=88)
        title = source.stem.replace("_", " ").capitalize()
        metadata = {**extracted.attributes, **extract_structured(extracted.text, media_type, name)}
        metadata["bundled_sample"] = True
        memories.append({
            "id": "sample-" + source.stem, "source_uri": "sample://" + name,
            "media_type": media_type, "title": title, "text": extracted.text.strip(),
            "created_at": "2026-10-01T00:00:00Z", "metadata": metadata,
            "labels": classify_image_context(extracted.text, name) if media_type.startswith("image/") else ["document"],
            "content_url": "asset://demo/originals/" + name,
            "thumbnail_url": "asset://demo/previews/" + preview_name,
        })
    (DEST / "memories.json").write_text(json.dumps(memories, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Bundled {len(memories)} samples with actual extracted text and previews.")


if __name__ == "__main__":
    main()
