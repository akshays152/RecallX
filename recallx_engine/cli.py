from __future__ import annotations

import argparse
import json
from pathlib import Path

from .engine import RecallEngine


def main() -> None:
    parser = argparse.ArgumentParser(prog="recallx", description="RecallX local memory engine")
    parser.add_argument("--db", default="recallx.db")
    commands = parser.add_subparsers(dest="command", required=True)
    ingest = commands.add_parser("ingest", help="Index files or folders")
    ingest.add_argument("paths", nargs="+")
    ingest.add_argument("--recursive", action="store_true")
    search = commands.add_parser("search", help="Search indexed memories")
    search.add_argument("query")
    search.add_argument("--limit", type=int, default=10)
    evaluate = commands.add_parser("evaluate", help="Evaluate labeled query relevance from JSON")
    evaluate.add_argument("dataset", help="JSON list with query and relevant_source_uris")
    evaluate.add_argument("--k", type=int, default=5)
    commands.add_parser("info", help="Show engine capabilities")
    serve = commands.add_parser("serve", help="Start the Android-facing HTTP API")
    serve.add_argument("--host", default="0.0.0.0")
    serve.add_argument("--port", type=int, default=8000)
    args = parser.parse_args()

    if args.command == "serve":
        try:
            import uvicorn
        except ImportError as error:
            raise SystemExit("Install API dependencies: pip install -e .[api]") from error
        uvicorn.run("recallx_engine.api:app", host=args.host, port=args.port, reload=False)
        return

    engine = RecallEngine(args.db)
    if args.command == "info":
        print(json.dumps(engine.capabilities(), indent=2))
    elif args.command == "search":
        print(json.dumps([result.to_dict() for result in engine.search(args.query, limit=args.limit)], indent=2, ensure_ascii=False))
    elif args.command == "evaluate":
        cases = json.loads(Path(args.dataset).read_text(encoding="utf-8"))
        reciprocal_ranks, hits = [], 0
        for case in cases:
            relevant = set(case["relevant_source_uris"])
            results = engine.search(case["query"], limit=args.k)
            rank = next((index for index, result in enumerate(results, 1) if result.memory.source_uri in relevant), None)
            hits += int(rank is not None)
            reciprocal_ranks.append(1 / rank if rank else 0)
        count = len(cases)
        print(json.dumps({
            "queries": count, "k": args.k, "recall_at_k": hits / count if count else 0,
            "mean_reciprocal_rank": sum(reciprocal_ranks) / count if count else 0,
        }, indent=2))
    else:
        files: list[Path] = []
        for raw_path in args.paths:
            path = Path(raw_path)
            files.extend(path.rglob("*") if path.is_dir() and args.recursive else (path.iterdir() if path.is_dir() else [path]))
        for path in filter(Path.is_file, files):
            try:
                memory, created = engine.ingest(path)
                print(json.dumps({"path": str(path), "id": memory.id, "created": created, "warning": memory.metadata.get("warning")}, ensure_ascii=False))
            except (OSError, ValueError) as error:
                print(json.dumps({"path": str(path), "error": str(error)}))


if __name__ == "__main__":
    main()
