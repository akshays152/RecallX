# RecallX submission runbook

## What the prototype actually delivers

RecallX has a Python Recall Engine and an Android client. The engine extracts text from images (local OCR), PDFs (including scanned pages), DOCX, plain text, and optionally audio; adds structured fields; indexes local embeddings; and returns ranked natural-language search results. The Android client can add a file or camera photo, save a pasted message/note, dictate a search query, browse results, open retained originals, and delete memories. Inference and storage run on the laptop/server, **not on the phone**. There is no automatic SMS or chat-app collection and no Office Kit/Snapdragon on-device inference.

## Reproduce the demo

1. Install Python 3.10+ and run `pip install -e ".[api,images,documents,semantic,audio]"`. To cache FastEmbed, set `RECALLX_ALLOW_MODEL_DOWNLOAD=1` before running the engine once while online. To cache Whisper, keep that setting while ingesting a sample audio file once. Then unset it for the offline demo. Without cached FastEmbed weights the text embedder falls back to hashing; audio transcription requires a cached Whisper model.
2. Run `python -m unittest discover -v`.
3. Run `python examples/generate_demo.py --out demo_data --db demo.db`. It generates four synthetic screenshots and one message, then reports Recall@1 for five fixed queries. This is a smoke check, not a claim of real-world accuracy.
4. Run `recallx --db demo.db serve --host 0.0.0.0 --port 8000`. Check `http://localhost:8000/health` on the laptop.
5. Install the Android debug APK. The emulator uses `http://10.0.2.2:8000/v1/`. For a physical phone, connect it to the same trusted Wi-Fi, choose **Server**, and enter `http://<laptop-LAN-IP>:8000/v1/`. Permit inbound TCP 8000 in the laptop firewall if necessary.
6. Search `hotel price`, open the matching result, then show ingestion with a fresh screenshot or PDF. Search a phrase visible in it; open the original; delete it; and verify it disappears from the library.

## Required checks before submitting

- Build the Android APK with Android SDK Platform 35, Build Tools 35.0.0, JDK 17, and `android/gradlew.bat :app:assembleDebug` (Windows) or `bash android/gradlew :app:assembleDebug` (macOS/Linux). Google SDK license acceptance must be done by the user who agrees to those terms. The Android build has **not** completed in the current workspace because those SDK packages are not installed.
- Install on an emulator and at least one physical phone; test file picker, camera permissions, upload, search, voice query, original-content opening, deletion, loading/error states, and phone-to-laptop connectivity.
- Test representative real data (dark screenshots, compressed images, scanned PDFs, noisy audio, Hindi/English text) with labeled queries. Report Recall@5 and mean reciprocal rank using `recallx --db <database> evaluate <labels.json> --k 5`. The synthetic five-query smoke check is insufficient for an accuracy claim.
- Confirm hackathon eligibility and pre-event code rules, repo visibility, allowed models/licenses, submission format, and demo video requirements from the current organizer rules.
- Use synthetic data for a public demo. The development API accepts local-network requests without authentication and allows cleartext HTTP; it must not be exposed to the internet or used for sensitive personal data on an untrusted network.
- Keep `demo.db`, `demo.files/`, and `demo_data/` out of Git. The existing `output/` report is a separate user artifact and is not part of this implementation.

## Known scope limits

The current engine uses OCR and text semantics for most images. True visual embeddings require the optional CLIP model and were not validated in this workspace. Image context labels are heuristics, not a trained object/product/landmark classifier. Message ingestion is manual rather than automatic. This is a demo prototype, not a production-ready private phone backup: authentication, encrypted transport/storage, background sync, and on-device inference remain future work.
