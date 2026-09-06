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

    def test_long_document_uses_passage_ranking(self):
        filler = "ordinary weekly planning notes " * 180
        self.engine.ingest_text(f"{filler} The hidden Wi-Fi password is mango-cello-77. {filler}", source_uri="doc://long")
        self.engine.ingest_text("A short grocery list with mangoes", source_uri="doc://list")
        results = self.engine.search("hidden Wi-Fi password")
        self.assertEqual(results[0].memory.source_uri, "doc://long")


if __name__ == "__main__":
    unittest.main()
