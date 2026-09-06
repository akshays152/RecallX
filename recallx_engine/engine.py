from __future__ import annotations

import hashlib
import math
import mimetypes
import re
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .embeddings import Embedder, cosine, expand_query, get_embedder, mean_normalized
from .extractors import detect_media_type, extract
from .metadata import extract_structured, parse_date_filter
from .models import Memory, SearchResult
from .store import MemoryStore


class RecallEngine:
    def __init__(self, db_path: str | Path = "recallx.db", embedder: Embedder | None = None):
        self.store = MemoryStore(db_path)
        self.embedder = embedder or get_embedder()

    def ingest(
        self, path: str | Path, *, source_uri: str | None = None, media_type: str | None = None,
        created_at: str | None = None, title: str | None = None, extra_metadata: dict[str, Any] | None = None,
    ) -> tuple[Memory, bool]:
        source = Path(path)
        if not source.is_file():
            raise FileNotFoundError(source)
        raw = source.read_bytes()
        content_hash = hashlib.sha256(raw).hexdigest()
        kind = detect_media_type(source, media_type)
        extracted = extract(source, kind)
        structured = extract_structured(extracted.text, kind, source.name)
        stat = source.stat()
        created = created_at or datetime.fromtimestamp(stat.st_mtime, timezone.utc).isoformat()
        now = datetime.now(timezone.utc).isoformat()
        metadata = {**extracted.attributes, **structured, **(extra_metadata or {})}
        memory = Memory(
            id=str(uuid.uuid4()), source_uri=source_uri or str(source.resolve()), media_type=kind,
            title=title or source.stem.replace("_", " ").replace("-", " "), text=extracted.text.strip(),
            created_at=created, indexed_at=now, metadata=metadata, labels=extracted.labels,
            content_hash=content_hash,
        )
        searchable = self._searchable_text(memory)
        vectors = [self.embedder.encode(searchable)]
        encode_image = getattr(self.embedder, "encode_image", None)
        if kind.startswith("image/") and encode_image:
            try:
                vectors.append(encode_image(str(source)))
                memory.metadata["visual_embedding"] = True
            except (OSError, RuntimeError, ValueError) as error:
                memory.metadata["visual_embedding_warning"] = str(error)
        chunks = [(chunk, self.embedder.encode(chunk)) for chunk in self._chunks(memory.text)]
        return self.store.upsert(memory, mean_normalized(vectors), chunks)

    def ingest_text(
        self, text: str, *, title: str = "Message", source_uri: str = "inline://message",
        media_type: str = "text/plain", created_at: str | None = None, extra_metadata: dict[str, Any] | None = None,
    ) -> tuple[Memory, bool]:
        digest = hashlib.sha256(f"{source_uri}\0{text}".encode()).hexdigest()
        now = datetime.now(timezone.utc).isoformat()
        metadata = {**extract_structured(text, media_type, title), **(extra_metadata or {})}
        memory = Memory(str(uuid.uuid4()), source_uri, media_type, title, text.strip(), created_at or now, now, metadata, [], digest)
        searchable = self._searchable_text(memory)
        chunks = [(chunk, self.embedder.encode(chunk)) for chunk in self._chunks(text)]
        return self.store.upsert(memory, self.embedder.encode(searchable), chunks)

    @staticmethod
    def _searchable_text(memory: Memory) -> str:
        metadata_values = " ".join(
            " ".join(map(str, value)) if isinstance(value, list) else str(value)
            for value in memory.metadata.values()
        )
        return f"{memory.title} {memory.text} {' '.join(memory.labels)} {metadata_values}"

    @staticmethod
    def _chunks(text: str, target_words: int = 140, overlap_words: int = 25) -> list[str]:
        words = text.split()
        if len(words) <= target_words:
            return [text] if text else []
        step = target_words - overlap_words
        return [" ".join(words[start : start + target_words]) for start in range(0, len(words), step)]

    @staticmethod
    def _tokens(text: str) -> set[str]:
        stop = {"a", "an", "the", "that", "this", "where", "i", "my", "me", "find", "saved", "show", "get", "was"}
        return {x for x in re.findall(r"\w+", text.casefold()) if len(x) > 1 and x not in stop}

    @staticmethod
    def _highlight(text: str, terms: set[str], limit: int = 2) -> list[str]:
        snippets: list[str] = []
        for line in filter(None, (x.strip() for x in text.splitlines())):
            if any(term in line.casefold() for term in terms):
                snippets.append(line[:220])
                if len(snippets) == limit:
                    break
        return snippets

    def search(
        self, query: str, *, limit: int = 10, media_type: str | None = None,
        document_type: str | None = None, date_from: str | None = None, date_to: str | None = None,
    ) -> list[SearchResult]:
        if not query.strip():
            return []
        expanded = expand_query(query)
        query_vector = self.embedder.encode(expanded)
        query_terms = self._tokens(expanded)
        start, end = parse_date_filter(date_from), parse_date_filter(date_to)
        results: list[SearchResult] = []
        for memory, vector in self.store.all():
            if media_type and not memory.media_type.startswith(media_type):
                continue
            if document_type and memory.metadata.get("document_type") != document_type:
                continue
            try:
                created = datetime.fromisoformat(memory.created_at.replace("Z", "+00:00"))
                if created.tzinfo is None and ((start and start.tzinfo) or (end and end.tzinfo)):
                    created = created.replace(tzinfo=timezone.utc)
                if start and start.tzinfo is None and created.tzinfo:
                    start = start.replace(tzinfo=timezone.utc)
                if end and end.tzinfo is None and created.tzinfo:
                    end = end.replace(tzinfo=timezone.utc)
                if start and created < start:
                    continue
                if end and created > end:
                    continue
            except ValueError:
                pass
            searchable = self._searchable_text(memory)
            memory_terms = self._tokens(searchable)
            overlap = len(query_terms & memory_terms) / math.sqrt(max(1, len(query_terms) * len(memory_terms)))
            document_score = cosine(query_vector, vector)
            chunk_scores = [(cosine(query_vector, chunk_vector), chunk_text) for chunk_text, chunk_vector in self.store.chunks(memory.id)]
            best_chunk_score, best_chunk = max(chunk_scores, default=(0.0, ""), key=lambda item: item[0])
            semantic = max(0.0, document_score, best_chunk_score)
            exact = 1.0 if query.casefold() in searchable.casefold() else 0.0
            score = 0.62 * semantic + 0.30 * overlap + 0.08 * exact
            reasons = []
            if semantic > 0.25:
                reasons.append("semantic match")
            matched = sorted(query_terms & memory_terms)
            if matched:
                reasons.append("matched: " + ", ".join(matched[:6]))
            if exact:
                reasons.append("exact phrase")
            if score > 0.015:
                highlight_source = best_chunk if best_chunk_score > document_score else memory.text
                results.append(SearchResult(memory, score, reasons, self._highlight(highlight_source, query_terms)))
        return sorted(results, key=lambda item: item.score, reverse=True)[: max(1, min(limit, 100))]

    def capabilities(self) -> dict[str, Any]:
        return {
            "embedding_model": self.embedder.name,
            "embedding_dimensions": self.embedder.dimensions,
            "indexed_memories": self.store.count(),
            "local_only": True,
            "supported_types": sorted(set(mimetypes.types_map.values()) & {
                "application/pdf", "image/jpeg", "image/png", "image/webp", "text/plain", "text/csv", "audio/mpeg", "audio/wav"
            }),
        }

    def close(self) -> None:
        self.store.close()

    def __enter__(self) -> "RecallEngine":
        return self

    def __exit__(self, *_: object) -> None:
        self.close()
