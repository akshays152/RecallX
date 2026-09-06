# RecallX Recall Engine

Local-first multimodal retrieval for the RecallX Android app. It indexes screenshots, photos, PDFs, text/messages, and voice notes; extracts searchable text and useful fields; and ranks natural-language queries such as **“Find that screenshot where I saved the hotel price.”**

## What is done as of now

- Image preprocessing and OCR (Pillow + Tesseract)
- PDF/DOCX/text extraction
- Voice-note transcription (optional local faster-whisper)
- Local semantic embeddings (SentenceTransformers when cached, deterministic offline fallback otherwise)
- Optional CLIP shared visual/text embeddings for image-content retrieval beyond OCR
- Context labels for screenshots, shopping, travel, maps, food, documents, and conversations
- Price, date, time, location, person, product/model, phone, email, URL, and document-type extraction
- Hybrid semantic + lexical retrieval with filters, explanations, and highlights
- Overlapping passage embeddings so details inside long PDFs remain retrievable
- Deduplication and persistent SQLite vector storage
- Android-friendly FastAPI contract plus a CLI

All content and vectors remain local. No cloud API or key is required.

## Quick start

```powershell
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -e ".[api,images,documents]"
recallx serve --host 0.0.0.0 --port 8000
```

For stronger semantic matching, install `pip install -e ".[semantic]"`. Models use the local cache by default, so startup never silently downloads model weights. Set `RECALLX_ALLOW_MODEL_DOWNLOAD=1` for a one-time approved download. Set `RECALLX_EMBEDDER=clip` to use CLIP for shared image/text embeddings or `RECALLX_EMBEDDER=sentence-transformer` for text-focused retrieval. For voice notes, install `pip install -e ".[audio]"`. Tesseract itself must be installed on the laptop/phone environment for OCR; the Python extra installs its adapter.

The zero-dependency fallback works immediately for text:

```powershell
python -m recallx_engine --db demo.db ingest .\samples --recursive
python -m recallx_engine --db demo.db search "hotel price screenshot"
python -m unittest discover -v
```

## Android/backend API contract

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/health` | Model, capabilities, and indexed count |
| `POST` | `/v1/memories/file` | Multipart upload (`file`, optional `source_uri`, `created_at`) |
| `POST` | `/v1/memories/text` | JSON ingestion for messages/clipboard text |
| `POST` | `/v1/search` | Natural-language search and optional filters |
| `GET` | `/v1/memories/{id}` | Retrieve one memory |
| `DELETE` | `/v1/memories/{id}` | Privacy deletion |

Example search body:

```json
{
  "query": "Find that screenshot where I saved the hotel price",
  "limit": 10,
  "media_type": "image/",
  "document_type": null,
  "date_from": null,
  "date_to": null
}
```

Each result includes a normalized score, the complete memory, matching reasons, and text highlights. `created: false` on ingestion means the bytes were already indexed.

## Model strategy for the hackathon

The engine is intentionally provider-independent. In `auto` mode it uses a locally cached `sentence-transformers/all-MiniLM-L6-v2` (open source, CPU-friendly); if unavailable it falls back to a deterministic 512-dimensional hashing model. CLIP can embed pixels and queries into the same vector space. OCR and Whisper also run locally. For Snapdragon deployment, preserve the API and replace the embedder/OCR adapters with ONNX or Qualcomm AI Hub builds—the retrieval and metadata layers do not change.

## Accuracy evaluation

Add representative phone memories and labeled queries, then measure Recall@K and mean reciprocal rank. The current tests cover the headline query, metadata extraction, filtering, and deduplication. Avoid tuning only on pristine documents: include dark-mode screenshots, compressed WhatsApp images, mixed Hindi/English text, currency variants, and vague time phrases.

An evaluation dataset is a JSON list like `[{"query":"hotel price", "relevant_source_uris":["content://media/42"]}]`. Run `recallx --db demo.db evaluate eval.json --k 5` to report Recall@5 and mean reciprocal rank.
