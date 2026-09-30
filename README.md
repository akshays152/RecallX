# RecallX

Local-first multimodal retrieval with an Android client and Python Recall Engine. It indexes screenshots, photos, PDFs, text/messages, and voice notes; extracts searchable text and useful fields; and ranks natural-language queries such as **“Find that screenshot where I saved the hotel price.”**

## What is done as of now

- Image preprocessing and local ONNX OCR (Pillow + RapidOCR)
- PDF/DOCX/text extraction
- Voice-note transcription (optional local faster-whisper)
- Local semantic embeddings (FastEmbed ONNX when cached, deterministic offline fallback otherwise)
- Optional CLIP shared visual/text embeddings for image-content retrieval beyond OCR
- Context labels for screenshots, shopping, travel, maps, food, documents, and conversations
- Price, date, time, location, person, product/model, phone, email, URL, and document-type extraction
- Hybrid semantic + lexical retrieval with filters, explanations, and highlights
- Overlapping passage embeddings so details inside long PDFs remain retrievable
- Deduplication and persistent SQLite vector storage
- Android-friendly FastAPI contract plus a CLI

All content and vectors remain on the computer running the Recall Engine. No cloud API or key is required. The Android app communicates with that computer over a local network; inference is **not yet on the phone**.

## Quick start

```powershell
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -e ".[api,images,documents]"
recallx serve --host 0.0.0.0 --port 8000
```

The `images` extra includes a local ONNX OCR model and needs no separate Tesseract installation. For stronger semantic matching, install `pip install -e ".[semantic]"` (FastEmbed ONNX). Models use the local cache by default, so startup never silently downloads model weights. Set `RECALLX_ALLOW_MODEL_DOWNLOAD=1` for a one-time approved download. Set `RECALLX_EMBEDDER=clip` and install `.[vision]` for shared image/text embeddings, or set `RECALLX_EMBEDDER=sentence-transformer` and install `.[sentence-transformer]` for the PyTorch text model. For voice notes, install `pip install -e ".[audio]"` and cache the Whisper model before an offline demo.

The zero-dependency fallback works immediately for text:

```powershell
python -m recallx_engine --db demo.db ingest .\samples --recursive
python -m recallx_engine --db demo.db search "hotel price screenshot"
python -m unittest discover -v
```

For a privacy-safe demonstration, run `python examples/generate_demo.py --out demo_data --db demo.db`. This creates four sample screenshots plus a message and checks whether five queries retrieve the expected memories. Start the API with `recallx --db demo.db serve --host 0.0.0.0 --port 8000` to browse this demo library in the Android app. See [submission runbook](docs/SUBMISSION.md) for the demo sequence and remaining validation.

## Android app

Open `android/` in Android Studio, install Android SDK Platform 35, and build/install the app with `./gradlew :app:assembleDebug` (or `gradlew.bat :app:assembleDebug` in Command Prompt). The app has file/photo ingestion, pasted message/note ingestion, camera capture, speech-to-text query input, search, a memory library, original-content opening, deletion, and an editable server address. On an emulator the default is `http://10.0.2.2:8000/v1/`. On a physical phone, connect phone and computer to the same trusted Wi-Fi and enter `http://<computer-LAN-IP>:8000/v1/` in **Server**. Allow port 8000 through the computer firewall if needed. Use demo data only on an untrusted network; the development API has no authentication.

The Android app is not yet verified on a physical device in this workspace. It depends on a running laptop service; on-device Snapdragon inference and Office Kit integration remain future work.

The current Android camera action uses the platform photo-capture contract and uploads the captured image as a new memory. The repository does not currently expose a visual-search endpoint, so this is not presented as backend visual search. A future CameraX/visual-retrieval phase should add both pieces together.

### Basic offline demo vs optional AI features

The basic demo requires only Python 3.10+, this package, and the API extra. Text ingestion and search work with the deterministic local hashing embedder and do not download model weights:

```powershell
pip install -e ".[api]"
```

Enhanced capabilities remain optional:

- `.[images]` — Pillow and RapidOCR ONNX image OCR
- `.[documents]` — PDF and DOCX extraction
- `.[semantic]` — cached FastEmbed semantic embeddings
- `.[vision]` — optional CLIP image/text embeddings
- `.[audio]` — optional faster-whisper voice transcription

If an optional package or cached model is unavailable, the engine reports the capability through `/health` or returns a clear ingestion warning/error; basic text operation remains available.

For Android builds, use Android SDK Platform 35, Build Tools 35.0.0, JDK 17, and the checked-in Gradle wrapper. The Android build has not been completed in this workspace because no Android SDK is configured.

## Android/backend API contract

| Method | Endpoint | Purpose |
|---|---|---|
| `GET` | `/health` | Model, capabilities, and indexed count |
| `POST` | `/v1/memories/file` | Multipart upload (`file`, optional `source_uri`, `created_at`) |
| `POST` | `/v1/memories/text` | JSON ingestion for messages/clipboard text |
| `GET` | `/v1/memories` | List memories, newest first |
| `POST` | `/v1/search` | Natural-language search and optional filters |
| `GET` | `/v1/memories/{id}` | Retrieve one memory |
| `GET` | `/v1/memories/{id}/content` | Retrieve original uploaded file |
| `GET` | `/v1/memories/{id}/thumbnail` | Retrieve image thumbnail, if available |
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

Each result includes a normalized score, the complete memory, matching reasons, and text highlights. `created: false` on ingestion means the bytes were already indexed. File uploads are retained under `recallx.files/` beside the SQLite database; deleting a memory removes the original and its thumbnail.

## Model strategy for the hackathon

The engine is intentionally provider-independent. In `auto` mode it prefers a locally cached FastEmbed `BAAI/bge-small-en-v1.5` ONNX model, then a cached SentenceTransformer, and finally a deterministic 512-dimensional hashing model. CLIP can embed pixels and queries into the same vector space when installed and cached. OCR uses a local ONNX model. For Snapdragon deployment, preserve the API and replace the embedder/OCR adapters with phone-native builds; the retrieval and metadata layers do not change.

## Accuracy evaluation

Add representative phone memories and labeled queries, then measure Recall@K and mean reciprocal rank. The current tests cover the headline query, metadata extraction, filtering, and deduplication. Avoid tuning only on pristine documents: include dark-mode screenshots, compressed WhatsApp images, mixed Hindi/English text, currency variants, and vague time phrases.

An evaluation dataset is a JSON list like `[{"query":"hotel price", "relevant_source_uris":["content://media/42"]}]`. Run `recallx --db demo.db evaluate eval.json --k 5` to report Recall@5 and mean reciprocal rank.
