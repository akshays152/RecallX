from __future__ import annotations

import os
import tempfile
from pathlib import Path
from typing import Annotated

try:
    from fastapi import FastAPI, File, Form, HTTPException, UploadFile
    from fastapi.responses import FileResponse
    from pydantic import BaseModel, Field
    from starlette.concurrency import run_in_threadpool
except ImportError as error:  # pragma: no cover
    raise RuntimeError("API dependencies missing. Run: pip install -e .[api]") from error

from .engine import RecallEngine


engine = RecallEngine(os.environ.get("RECALLX_DB", "recallx.db"))
app = FastAPI(title="RecallX Recall Engine", version="0.1.0")


@app.on_event("shutdown")
def close_engine() -> None:
    """Release SQLite before a test/dev server process exits."""
    engine.close()


def memory_response(memory) -> dict:
    result = memory.to_dict()
    result["content_url"] = f"/v1/memories/{memory.id}/content" if engine.store.get_file(memory.id) else None
    result["thumbnail_url"] = f"/v1/memories/{memory.id}/thumbnail" if engine.store.get_file(memory.id, thumbnail=True) else None
    return result


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
    return {"created": created, "memory": memory_response(memory)}


@app.post("/v1/memories/file", status_code=201)
async def ingest_file(
    file: Annotated[UploadFile, File()], source_uri: Annotated[str | None, Form()] = None,
    created_at: Annotated[str | None, Form()] = None,
) -> dict:
    suffix = Path(file.filename or "upload").suffix
    temp_path: str | None = None
    try:
        size = 0
        with tempfile.NamedTemporaryFile(delete=False, suffix=suffix) as temporary:
            temp_path = temporary.name
            while chunk := await file.read(1024 * 1024):
                size += len(chunk)
                if size > 50 * 1024 * 1024:
                    raise HTTPException(413, "Maximum upload size is 50 MB")
                temporary.write(chunk)
        memory, created = await run_in_threadpool(
            engine.ingest,
            temp_path, title=Path(file.filename or "upload").stem,
            source_uri=source_uri or f"upload://{file.filename}", media_type=file.content_type,
            created_at=created_at,
        )
        return {"created": created, "memory": memory_response(memory)}
    except (OSError, ValueError) as error:
        raise HTTPException(422, str(error)) from error
    finally:
        if temp_path:
            Path(temp_path).unlink(missing_ok=True)


@app.post("/v1/search")
def search(request: SearchRequest) -> dict:
    results = engine.search(**request.model_dump())
    return {"query": request.query, "count": len(results), "results": [
        {**item.to_dict(), "memory": memory_response(item.memory)} for item in results
    ]}


@app.get("/v1/memories")
def list_memories(limit: int = 100, offset: int = 0) -> dict:
    if limit < 1 or limit > 500 or offset < 0:
        raise HTTPException(422, "limit must be 1-500 and offset must be nonnegative")
    memories = engine.store.list(limit, offset)
    return {"count": len(memories), "total": engine.store.count(), "memories": [memory_response(m) for m in memories]}


@app.get("/v1/memories/{memory_id}")
def get_memory(memory_id: str) -> dict:
    memory = engine.store.get(memory_id)
    if not memory:
        raise HTTPException(404, "Memory not found")
    return memory_response(memory)


@app.get("/v1/memories/{memory_id}/content")
def get_content(memory_id: str):
    memory = engine.store.get(memory_id)
    path = engine.store.get_file(memory_id)
    if not memory or not path or not Path(path).is_file():
        raise HTTPException(404, "Content not found")
    return FileResponse(path, media_type=memory.media_type, filename=Path(memory.source_uri).name)


@app.get("/v1/memories/{memory_id}/thumbnail")
def get_thumbnail(memory_id: str):
    path = engine.store.get_file(memory_id, thumbnail=True)
    if not path or not Path(path).is_file():
        raise HTTPException(404, "Thumbnail not found")
    return FileResponse(path, media_type="image/jpeg")


@app.delete("/v1/memories/{memory_id}")
def delete_memory(memory_id: str) -> dict:
    if not engine.delete(memory_id):
        raise HTTPException(404, "Memory not found")
    return {"deleted": True, "id": memory_id}
