from __future__ import annotations

from dataclasses import asdict, dataclass, field
from typing import Any


@dataclass(slots=True)
class Memory:
    id: str
    source_uri: str
    media_type: str
    title: str
    text: str
    created_at: str
    indexed_at: str
    metadata: dict[str, Any] = field(default_factory=dict)
    labels: list[str] = field(default_factory=list)
    content_hash: str = ""

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass(slots=True)
class SearchResult:
    memory: Memory
    score: float
    reasons: list[str] = field(default_factory=list)
    highlights: list[str] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        return {
            "memory": self.memory.to_dict(),
            "score": round(self.score, 6),
            "reasons": self.reasons,
            "highlights": self.highlights,
        }

