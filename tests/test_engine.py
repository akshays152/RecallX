import tempfile
import unittest
from pathlib import Path

from recallx_engine.embeddings import HashingEmbedder
from recallx_engine.engine import RecallEngine
from recallx_engine.metadata import extract_structured


class RecallEngineTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.engine = RecallEngine(Path(self.directory.name) / "test.db", HashingEmbedder())

    def tearDown(self):
        self.engine.close()
        self.directory.cleanup()

    @staticmethod
    def image_font():
        from PIL import ImageFont
        candidates = [
            "C:/Windows/Fonts/arial.ttf",
            "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
            "/usr/share/fonts/dejavu-sans-fonts/DejaVuSans.ttf",
        ]
        for candidate in candidates:
            if Path(candidate).is_file():
                return ImageFont.truetype(candidate, 48)
        raise unittest.SkipTest("No test font available")

    def test_hotel_price_semantic_retrieval(self):
        self.engine.ingest_text("Taj Lakefront hotel room total ₹8,499 for 12 October 2026", title="Booking screenshot")
        self.engine.ingest_text("Team standup moved to 10:30", title="Chat")
        results = self.engine.search("Find that screenshot where I saved the hotel price")
        self.assertTrue(results)
        self.assertIn("Taj Lakefront", results[0].memory.text)
        self.assertIn("₹8,499", results[0].memory.metadata["prices"])

    def test_deduplicates_same_content(self):
        first, first_created = self.engine.ingest_text("same", source_uri="sms://1")
        second, second_created = self.engine.ingest_text("same", source_uri="sms://1")
        self.assertTrue(first_created)
        self.assertFalse(second_created)
        self.assertEqual(first.id, second.id)

    def test_filters_document_type(self):
        self.engine.ingest_text("Invoice number 42. Bill to Alex. Total INR 500", title="Paper")
        self.engine.ingest_text("Buy now for INR 600", title="Store")
        results = self.engine.search("INR", document_type="invoice")
        self.assertEqual(len(results), 1)
        self.assertEqual(results[0].memory.metadata["document_type"], "invoice")

    def test_structured_information(self):
        data = extract_structured("Email me@example.com. Meet with Riya Sharma at Cubbon Park on 2026-10-09. Pay Rs. 1,250.", "text/plain")
        self.assertIn("me@example.com", data["emails"])
        self.assertIn("Rs. 1,250", data["prices"])
        self.assertIn("2026-10-09", data["dates"])
        self.assertIn("Riya Sharma", data["people"])
        self.assertNotIn("2026", data.get("products", []))

    def test_long_document_uses_passage_ranking(self):
        filler = "ordinary weekly planning notes " * 180
        self.engine.ingest_text(f"{filler} The hidden Wi-Fi password is mango-cello-77. {filler}", source_uri="doc://long")
        self.engine.ingest_text("A short grocery list with mangoes", source_uri="doc://list")
        results = self.engine.search("hidden Wi-Fi password")
        self.assertEqual(results[0].memory.source_uri, "doc://long")

    def test_file_is_retained_and_deleted_with_memory(self):
        source = Path(self.directory.name) / "hotel-note.txt"
        source.write_text("Hotel room price INR 7200", encoding="utf-8")
        memory, created = self.engine.ingest(source, source_uri="content://hotel-note")
        self.assertTrue(created)
        stored = Path(self.engine.store.get_file(memory.id))
        self.assertEqual(stored.read_text(encoding="utf-8"), source.read_text(encoding="utf-8"))
        self.assertEqual(self.engine.store.list()[0].id, memory.id)
        self.assertTrue(self.engine.delete(memory.id))
        self.assertFalse(stored.exists())
        self.assertEqual(self.engine.store.count(), 0)

    def test_image_ocr_when_installed(self):
        try:
            from PIL import Image, ImageDraw, ImageFont
            from rapidocr_onnxruntime import RapidOCR
        except ImportError:
            self.skipTest("image extra not installed")
        source = Path(self.directory.name) / "Screenshot_hotel.png"
        image = Image.new("RGB", (900, 220), "white")
        font = self.image_font()
        ImageDraw.Draw(image).text((30, 50), "HOTEL PRICE INR 7200", fill="black", font=font)
        image.save(source)
        memory, _ = self.engine.ingest(source)
        self.assertIn("HOTEL PRICE INR 7200", memory.text)
        self.assertIn("screenshot", memory.labels)
        self.assertEqual(self.engine.search("hotel price")[0].memory.id, memory.id)

    def test_scanned_pdf_ocr_when_installed(self):
        try:
            from PIL import Image, ImageDraw, ImageFont
            import pymupdf
            from rapidocr_onnxruntime import RapidOCR
        except ImportError:
            self.skipTest("document and image extras not installed")
        image_path = Path(self.directory.name) / "scan.png"
        image = Image.new("RGB", (900, 220), "white")
        font = self.image_font()
        ImageDraw.Draw(image).text((30, 50), "HOTEL PRICE INR 7200", fill="black", font=font)
        image.save(image_path)
        pdf_path = Path(self.directory.name) / "scan.pdf"
        document = pymupdf.open()
        page = document.new_page(width=900, height=220)
        page.insert_image(page.rect, filename=str(image_path))
        document.save(str(pdf_path))
        document.close()
        memory, _ = self.engine.ingest(pdf_path)
        self.assertIn("HOTEL PRICE INR 7200", memory.text)
        self.assertEqual(memory.metadata["extractor"], "pypdf+ocr")

    def test_model_change_reindexes_existing_memories(self):
        memory, _ = self.engine.ingest_text("Hotel room INR 7200", source_uri="demo://model-change")
        self.engine.close()
        self.engine = RecallEngine(Path(self.directory.name) / "test.db", HashingEmbedder(64))
        self.assertEqual(self.engine.store.get_setting("embedding_model"), "hashing-local-v1:64")
        self.assertEqual(self.engine.search("hotel room price")[0].memory.id, memory.id)


if __name__ == "__main__":
    unittest.main()
