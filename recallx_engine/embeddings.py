from __future__ import annotations

import hashlib
import math
import os
import re
from functools import lru_cache
from typing import Protocol


class Embedder(Protocol):
    name: str
    dimensions: int

    def encode(self, text: str) -> list[float]: ...


_CONCEPTS = {
    "hotel": "stay accommodation room booking resort hostel travel",
    "price": "cost amount total fare rate rupees inr money paid payment",
    "screenshot": "screen capture image photo saved gallery",
    "flight": "airline airport ticket boarding travel fare",
    "restaurant": "food dining cafe menu meal",
    "receipt": "invoice bill purchase payment total merchant",
    "meeting": "call appointment calendar event discussion",
    "address": "location place map directions venue",
    "phone": "mobile contact number call",
    "document": "pdf file form letter report",
}


def expand_query(text: str) -> str:
    lowered = text.lower()
    additions = [value for key, value in _CONCEPTS.items() if key in lowered]
    return f"{text} {' '.join(additions)}".strip()


class HashingEmbedder:
    """Dependency-free, deterministic local embedding using word and character features."""

    name = "hashing-local-v1"

    def __init__(self, dimensions: int = 512):
        self.dimensions = dimensions

    @staticmethod
    def _features(text: str) -> list[tuple[str, float]]:
        words = re.findall(r"[\w@.+₹$€£-]+", text.casefold(), re.UNICODE)
        features: list[tuple[str, float]] = []
        for word in words:
            features.append(("w:" + word, 1.7))
            padded = f"^{word}$"
            for n in (3, 4):
                features.extend((f"c{n}:" + padded[i : i + n], 0.35) for i in range(max(0, len(padded) - n + 1)))
        features.extend(("b:" + a + "_" + b, 0.8) for a, b in zip(words, words[1:]))
        return features

    def encode(self, text: str) -> list[float]:
        vector = [0.0] * self.dimensions
        for feature, weight in self._features(text):
            digest = hashlib.blake2b(feature.encode("utf-8"), digest_size=8).digest()
            value = int.from_bytes(digest, "little")
            index = value % self.dimensions
            vector[index] += weight if value & (1 << 63) else -weight
        norm = math.sqrt(sum(x * x for x in vector))
        return [x / norm for x in vector] if norm else vector


class SentenceTransformerEmbedder:
    name = "sentence-transformers/all-MiniLM-L6-v2"
    dimensions = 384

    def __init__(self, model_name: str | None = None):
        from sentence_transformers import SentenceTransformer

        self.name = model_name or self.name
        self._model = SentenceTransformer(self.name, local_files_only=True)
        self.dimensions = int(self._model.get_sentence_embedding_dimension())

    def encode(self, text: str) -> list[float]:
        return self._model.encode(text, normalize_embeddings=True).tolist()


class ClipEmbedder:
    """Shared text/image embedding space for true visual-to-text retrieval."""

    name = "openai/clip-vit-base-patch32"
    dimensions = 512

    def __init__(self, model_name: str | None = None, allow_download: bool = False):
        from transformers import CLIPModel, CLIPProcessor

        self.name = model_name or self.name
        self._model = CLIPModel.from_pretrained(self.name, local_files_only=not allow_download)
        self._processor = CLIPProcessor.from_pretrained(self.name, local_files_only=not allow_download)

    @staticmethod
    def _normalize(values: list[float]) -> list[float]:
        norm = math.sqrt(sum(x * x for x in values))
        return [x / norm for x in values] if norm else values

    def encode(self, text: str) -> list[float]:
        inputs = self._processor(text=[text], return_tensors="pt", padding=True)
        return self._normalize(self._model.get_text_features(**inputs)[0].detach().cpu().tolist())

    def encode_image(self, path: str) -> list[float]:
        from PIL import Image

        with Image.open(path) as image:
            inputs = self._processor(images=image.convert("RGB"), return_tensors="pt")
        return self._normalize(self._model.get_image_features(**inputs)[0].detach().cpu().tolist())


@lru_cache(maxsize=1)
def get_embedder(prefer_transformer: bool = True) -> Embedder:
    choice = os.environ.get("RECALLX_EMBEDDER", "auto").casefold()
    allow_download = os.environ.get("RECALLX_ALLOW_MODEL_DOWNLOAD", "0") == "1"
    if choice == "clip":
        try:
            return ClipEmbedder(allow_download=allow_download)
        except (ImportError, OSError, RuntimeError):
            pass
    if prefer_transformer and choice in {"auto", "sentence-transformer"}:
        try:
            return SentenceTransformerEmbedder()
        except (ImportError, OSError, RuntimeError):
            pass
    return HashingEmbedder()


def mean_normalized(vectors: list[list[float]]) -> list[float]:
    vectors = [vector for vector in vectors if vector]
    if not vectors:
        return []
    averaged = [sum(values) / len(vectors) for values in zip(*vectors)]
    norm = math.sqrt(sum(x * x for x in averaged))
    return [x / norm for x in averaged] if norm else averaged


def cosine(a: list[float], b: list[float]) -> float:
    if not a or not b or len(a) != len(b):
        return 0.0
    return max(-1.0, min(1.0, sum(x * y for x, y in zip(a, b))))
