from __future__ import annotations

import os
import tempfile
from pathlib import Path
from typing import Annotated

try:
    from fastapi import FastAPI, File, Form, HTTPException, UploadFile
    from pydantic import BaseModel, Field
except ImportError as error:  # pragma: no cover
    raise RuntimeError("API dependencies missing. Run: pip install -e .[api]") from error

from .engine import RecallEngine


engine = RecallEngine(os.environ.get("RECALLX_DB", "recallx.db"))
app = FastAPI(title="RecallX Recall Engine", version="0.1.0")


class TextMemoryRequest(BaseModel):
    text: str = Field(min_length=1)
    title: str = "Message"
    source_uri: str = "inline://message"
    created_at: str | None = None
    metadata: dict = Field(default_factory=dict)


class SearchRequest(BaseModel):
    query: str = Field(min_length=1)
    limit: int = Field(default=10, ge=1, le=100)
    media_type: str | None = None
    document_type: str | None = None
    date_from: str | None = None
    date_to: str | None = None


@app.get("/health")
def health() -> dict:
    return {"status": "ok", **engine.capabilities()}


@app.post("/v1/memories/text", status_code=201)
def ingest_text(request: TextMemoryRequest) -> dict:
    memory, created = engine.ingest_text(
        request.text, title=request.title, source_uri=request.source_uri,
        created_at=request.created_at, extra_metadata=request.metadata,
    )
    return {"created": created, "memory": memory.to_dict()}


@app.post("/v1/memories/file", status_code=201)
async def ingest_file(
    file: Annotated[UploadFile, File()], source_uri: Annotated[str | None, Form()] = None,
    created_at: Annotated[str | None, Form()] = None,
) -> dict:
    suffix = Path(file.filename or "upload").suffix
    temp_path: str | None = None
    try:
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temporary:
            temp_path = temporary.name
            while chunk := await file.read(1024 * 1024):
                temporary.write(chunk)
        memory, created = engine.ingest(
            temp_path, title=Path(file.filename or "upload").stem,
            source_uri=source_uri or f"upload://{file.filename}", media_type=file.content_type,
            created_at=created_at,
        )
        return {"created": created, "memory": memory.to_dict()}
    except (OSError, ValueError) as error:
        raise HTTPException(422, str(error)) from error
    finally:
        if temp_path:
            Path(temp_path).unlink(missing_ok=True)


@app.post("/v1/search")
def search(request: SearchRequest) -> dict:
    results = engine.search(**request.model_dump())
    return {"query": request.query, "count": len(results), "results": [item.to_dict() for item in results]}


@app.get("/v1/memories/{memory_id}")
def get_memory(memory_id: str) -> dict:
    memory = engine.store.get(memory_id)
    if not memory:
        raise HTTPException(404, "Memory not found")
    return memory.to_dict()


@app.delete("/v1/memories/{memory_id}")
def delete_memory(memory_id: str) -> dict:
    if not engine.store.delete(memory_id):
        raise HTTPException(404, "Memory not found")
    return {"deleted": True, "id": memory_id}
