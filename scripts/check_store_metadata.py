#!/usr/bin/env -S uv run --script
# SPDX-License-Identifier: Apache-2.0
# /// script
# requires-python = ">=3.11"
# dependencies = ["pillow==11.3.0"]
# ///
"""Check Play listing limits and committed store image properties."""

from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
LISTING = ROOT / "fastlane/metadata/android/en-US"
ASSETS = ROOT / "distribution/store-assets"


def check_text():
    required = {"title.txt": 30, "short_description.txt": 80, "full_description.txt": 4000}
    if not LISTING.exists():
        print("Listing absent in this branch; text checks activate when en-US is integrated")
        return
    for name, limit in required.items():
        path = LISTING / name
        value = path.read_text(encoding="utf-8").strip()
        if not value or len(value) > limit:
            raise ValueError(f"{path}: expected 1–{limit} characters, got {len(value)}")
        if name != "full_description.txt" and "\n" in value:
            raise ValueError(f"{path}: unexpected line break")
        print(f"Checked {path.relative_to(ROOT)}: {len(value)}/{limit} characters")
    for path in sorted((LISTING / "changelogs").glob("*.txt")):
        value = path.read_text(encoding="utf-8").strip()
        if not value or len(value) > 500:
            raise ValueError(f"{path}: expected 1–500 characters, got {len(value)}")
        print(f"Checked {path.relative_to(ROOT)}: {len(value)}/500 characters")


def check_png(name, size, mode, limit=None):
    path = ASSETS / name
    with Image.open(path) as image:
        if image.format != "PNG" or image.size != size or image.mode != mode:
            raise ValueError(f"{path}: expected {size} {mode} PNG")
        image.verify()
    if limit and path.stat().st_size > limit:
        raise ValueError(f"{path}: exceeds {limit} bytes")
    with Image.open(path) as image:
        if mode == "RGBA" and image.getchannel("A").getextrema() != (255, 255):
            raise ValueError(f"{path}: expected opaque pixels")
    print(f"Checked {path.relative_to(ROOT)}: {size}, {mode}, {path.stat().st_size} bytes")


def main():
    check_text()
    check_png("icon.png", (512, 512), "RGBA", 1024 * 1024)
    check_png("featureGraphic.png", (1024, 500), "RGB")


if __name__ == "__main__":
    main()
