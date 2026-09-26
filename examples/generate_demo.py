"""Create a privacy-safe RecallX demo library and print retrieval checks.

Run after installing: pip install -e ".[api,images,documents]"
Then: python examples/generate_demo.py --out demo_data --db demo.db
"""

from __future__ import annotations

import argparse
from pathlib import Path

from recallx_engine import RecallEngine


def font(size: int):
    from PIL import ImageFont

    for candidate in (
        "C:/Windows/Fonts/arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/usr/share/fonts/dejavu-sans-fonts/DejaVuSans.ttf",
    ):
        if Path(candidate).is_file():
            return ImageFont.truetype(candidate, size)
    return ImageFont.load_default()


def screenshot(path: Path, heading: str, lines: list[str]) -> None:
    from PIL import Image, ImageDraw

    image = Image.new("RGB", (1050, 680), "#f8fafc")
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle((40, 50, 1010, 620), radius=28, fill="white", outline="#cbd5e1", width=3)
    draw.rectangle((40, 50, 1010, 140), fill="#4f46e5")
    draw.text((75, 75), heading, font=font(42), fill="white")
    for index, line in enumerate(lines):
        draw.text((75, 180 + 82 * index), line, font=font(37), fill="#111827")
    image.save(path)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="demo_data")
    parser.add_argument("--db", default="demo.db")
    args = parser.parse_args()
    output = Path(args.out)
    output.mkdir(parents=True, exist_ok=True)

    samples = [
        ("Screenshot_hotel.png", "Stay Finder", ["Hotel Imperial, Bengaluru", "12 Oct - 14 Oct 2026", "Room price INR 7200", "Booking ID HX4827"], "hotel room price"),
        ("Screenshot_flight.png", "Skyline Air", ["Flight BLR to DEL", "Departure 09 Oct 2026 07:45", "PNR QW6J2A", "Fare INR 5630"], "flight booking PNR"),
        ("Screenshot_shopping.png", "Shopping Cart", ["Noise headphones Model N45", "Delivery to Bengaluru", "Total INR 2499"], "headphones shopping price"),
        ("Screenshot_restaurant.png", "Dinner Booking", ["Olive Garden Restaurant", "Table for 2 on 10 Oct 2026", "Near Cubbon Park", "Booking ID RS91"], "restaurant dinner booking"),
    ]

    with RecallEngine(args.db) as engine:
        for filename, heading, lines, _ in samples:
            path = output / filename
            screenshot(path, heading, lines)
            engine.ingest(path, source_uri=f"demo://{filename}")
        engine.ingest_text(
            "Meet Riya Sharma at Cubbon Park on 11 Oct 2026 at 10:30. Bring the project deck.",
            title="Message from Riya", source_uri="demo://message-riya",
        )
        checks = [(query, f"demo://{filename}") for filename, _, _, query in samples]
        checks.append(("meeting with Riya", "demo://message-riya"))
        correct = 0
        for query, expected in checks:
            results = engine.search(query, limit=1)
            found = results[0].memory.source_uri if results else None
            correct += found == expected
            print(f"{'PASS' if found == expected else 'FAIL'}  {query} -> {found}")
        print(f"Recall@1: {correct}/{len(checks)}")
        print(f"Indexed memories: {engine.store.count()}")


if __name__ == "__main__":
    main()
