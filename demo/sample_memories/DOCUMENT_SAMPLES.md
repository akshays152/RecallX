# Synthetic document samples

These three searchable PDFs contain fictional data. The Android APK now includes them, along with all six image samples, in its default **Samples** library. Browsing and keyword searching these prepared samples works without a server. **My memories** and **Add memory** continue to use the existing Recall Engine.

| File | Suggested search |
|---|---|
| `equipment_invoice.pdf` | `laptop stand invoice amount paid` |
| `goa_travel_itinerary.pdf` | `Goa hotel check-in breakfast` |
| `design_review_notes.pdf` | `Riya PDF ingestion deadline` |

For live ingestion testing, transfer the PDFs to your phone, then use **Add memory > Add file or photo** in RecallX. With the Recall Engine running and connected, select a PDF and search using one of the queries above. This is optional; judges can try the bundled Samples library immediately.

For a separate test database on your laptop, with document dependencies installed:

```powershell
python -m recallx_engine --db document-demo.db ingest demo/sample_memories/equipment_invoice.pdf demo/sample_memories/goa_travel_itinerary.pdf demo/sample_memories/design_review_notes.pdf
python -m recallx_engine --db document-demo.db serve --host 0.0.0.0 --port 8000
```

The existing image samples and backend behavior are unchanged.

To rebuild the app's bundled originals, previews, and pre-extracted sample text, run `python examples/build_android_demo_assets.py` with the image/document dependencies installed, then build the Android APK. No inference runs during sample browsing. Hiding a sample affects only its local visibility; **Settings > Restore sample library** restores the full set.
