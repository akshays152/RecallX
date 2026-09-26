from __future__ import annotations

import json
import sqlite3
from pathlib import Path
from threading import RLock

from .models import Memory


class MemoryStore:
    def __init__(self, path: str | Path):
        self.path = str(path)
        self._lock = RLock()
        self._connection = sqlite3.connect(self.path, check_same_thread=False)
        self._connection.row_factory = sqlite3.Row
        self._connection.executescript(
            """
            CREATE TABLE IF NOT EXISTS memories (
                id TEXT PRIMARY KEY, source_uri TEXT NOT NULL, media_type TEXT NOT NULL,
                title TEXT NOT NULL, text TEXT NOT NULL, created_at TEXT NOT NULL,
                indexed_at TEXT NOT NULL, metadata TEXT NOT NULL, labels TEXT NOT NULL,
                content_hash TEXT NOT NULL UNIQUE, embedding TEXT NOT NULL
            );
            CREATE INDEX IF NOT EXISTS memories_created_idx ON memories(created_at DESC);
            CREATE TABLE IF NOT EXISTS memory_chunks (
                memory_id TEXT NOT NULL, chunk_index INTEGER NOT NULL,
                text TEXT NOT NULL, embedding TEXT NOT NULL,
                PRIMARY KEY(memory_id, chunk_index),
                FOREIGN KEY(memory_id) REFERENCES memories(id) ON DELETE CASCADE
            );
            CREATE TABLE IF NOT EXISTS memory_files (
                memory_id TEXT PRIMARY KEY, original_path TEXT NOT NULL,
                thumbnail_path TEXT,
                FOREIGN KEY(memory_id) REFERENCES memories(id) ON DELETE CASCADE
            );
            CREATE TABLE IF NOT EXISTS settings (
                key TEXT PRIMARY KEY, value TEXT NOT NULL
            );
            """
        )

    def upsert(
        self, memory: Memory, embedding: list[float],
        chunks: list[tuple[str, list[float]]] | None = None,
    ) -> tuple[Memory, bool]:
        with self._lock:
            existing = self._connection.execute("SELECT * FROM memories WHERE content_hash = ?", (memory.content_hash,)).fetchone()
            if existing:
                return self._memory(existing), False
            self._connection.execute(
                "INSERT INTO memories VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (memory.id, memory.source_uri, memory.media_type, memory.title, memory.text,
                 memory.created_at, memory.indexed_at, json.dumps(memory.metadata), json.dumps(memory.labels),
                 memory.content_hash, json.dumps(embedding)),
            )
            self._connection.executemany(
                "INSERT INTO memory_chunks(memory_id, chunk_index, text, embedding) VALUES (?, ?, ?, ?)",
                ((memory.id, index, text, json.dumps(vector)) for index, (text, vector) in enumerate(chunks or [])),
            )
            self._connection.commit()
            return memory, True

    @staticmethod
    def _memory(row: sqlite3.Row) -> Memory:
        return Memory(
            id=row["id"], source_uri=row["source_uri"], media_type=row["media_type"],
            title=row["title"], text=row["text"], created_at=row["created_at"], indexed_at=row["indexed_at"],
            metadata=json.loads(row["metadata"]), labels=json.loads(row["labels"]), content_hash=row["content_hash"],
        )

    def all(self) -> list[tuple[Memory, list[float]]]:
        with self._lock:
            rows = self._connection.execute("SELECT * FROM memories ORDER BY created_at DESC").fetchall()
        return [(self._memory(row), json.loads(row["embedding"])) for row in rows]

    def get(self, memory_id: str) -> Memory | None:
        with self._lock:
            row = self._connection.execute("SELECT * FROM memories WHERE id = ?", (memory_id,)).fetchone()
        return self._memory(row) if row else None

    def list(self, limit: int = 100, offset: int = 0) -> list[Memory]:
        with self._lock:
            rows = self._connection.execute(
                "SELECT * FROM memories ORDER BY created_at DESC LIMIT ? OFFSET ?", (limit, offset)
            ).fetchall()
        return [self._memory(row) for row in rows]

    def set_file(self, memory_id: str, original_path: str, thumbnail_path: str | None) -> None:
        with self._lock:
            self._connection.execute(
                "INSERT OR REPLACE INTO memory_files VALUES (?, ?, ?)",
                (memory_id, original_path, thumbnail_path),
            )
            self._connection.commit()

    def get_file(self, memory_id: str, thumbnail: bool = False) -> str | None:
        with self._lock:
            row = self._connection.execute(
                "SELECT original_path, thumbnail_path FROM memory_files WHERE memory_id = ?", (memory_id,)
            ).fetchone()
        if not row:
            return None
        return row["thumbnail_path" if thumbnail else "original_path"]

    def chunks(self, memory_id: str) -> list[tuple[str, list[float]]]:
        with self._lock:
            rows = self._connection.execute(
                "SELECT text, embedding FROM memory_chunks WHERE memory_id = ? ORDER BY chunk_index", (memory_id,)
            ).fetchall()
        return [(row["text"], json.loads(row["embedding"])) for row in rows]

    def delete(self, memory_id: str) -> bool:
        with self._lock:
            self._connection.execute("DELETE FROM memory_chunks WHERE memory_id = ?", (memory_id,))
            self._connection.execute("DELETE FROM memory_files WHERE memory_id = ?", (memory_id,))
            cursor = self._connection.execute("DELETE FROM memories WHERE id = ?", (memory_id,))
            self._connection.commit()
        return cursor.rowcount > 0

    def count(self) -> int:
        return int(self._connection.execute("SELECT COUNT(*) FROM memories").fetchone()[0])

    def get_setting(self, key: str) -> str | None:
        with self._lock:
            row = self._connection.execute("SELECT value FROM settings WHERE key = ?", (key,)).fetchone()
        return row[0] if row else None

    def set_setting(self, key: str, value: str) -> None:
        with self._lock:
            self._connection.execute("INSERT OR REPLACE INTO settings VALUES (?, ?)", (key, value))
            self._connection.commit()

    def replace_embedding(self, memory_id: str, embedding: list[float], chunks: list[tuple[str, list[float]]]) -> None:
        with self._lock:
            self._connection.execute("UPDATE memories SET embedding = ? WHERE id = ?", (json.dumps(embedding), memory_id))
            self._connection.execute("DELETE FROM memory_chunks WHERE memory_id = ?", (memory_id,))
            self._connection.executemany(
                "INSERT INTO memory_chunks VALUES (?, ?, ?, ?)",
                ((memory_id, index, text, json.dumps(vector)) for index, (text, vector) in enumerate(chunks)),
            )
            self._connection.commit()

    def close(self) -> None:
        with self._lock:
            self._connection.close()
