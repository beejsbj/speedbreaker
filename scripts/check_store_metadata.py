#!/usr/bin/env -S uv run --script
# SPDX-License-Identifier: Apache-2.0
# /// script
# requires-python = ">=3.11"
# dependencies = ["pillow==11.3.0"]
# ///
"""Check Play listing limits and committed store image properties."""

import argparse
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
LISTING = ROOT / "fastlane/metadata/android/en-US"
ASSETS = ROOT / "distribution/store-assets"
SCREENSHOTS = LISTING / "images/phoneScreenshots"


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


def check_phone_screenshots(directory, required=False):
    paths = sorted(path for path in directory.glob("*")
                   if path.suffix.lower() in {".png", ".jpg", ".jpeg"})
    if not paths and not required:
        print("Phone screenshots absent; the store upload packet remains incomplete")
        return
    if not 2 <= len(paths) <= 8:
        raise ValueError(f"{directory}: expected 2–8 phone screenshots, got {len(paths)}")
    for path in paths:
        with Image.open(path) as image:
            width, height = image.size
            if image.format not in {"JPEG", "PNG"} or image.mode != "RGB":
                raise ValueError(f"{path}: expected RGB JPEG or 24-bit PNG without alpha")
            if min(width, height) < 320 or max(width, height) > 3840:
                raise ValueError(f"{path}: dimensions must be between 320 and 3840 pixels")
            if max(width, height) > 2 * min(width, height):
                raise ValueError(f"{path}: longest side exceeds twice the shortest side")
            image.verify()
        # JPEG verify() only checks headers. Decode the pixels as well so a
        # truncated/corrupt upload cannot pass merely by retaining valid dimensions.
        with Image.open(path) as image:
            image.load()
        print(f"Checked phone screenshot {path.name}: {width}×{height}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--screenshots-dir", type=Path, default=SCREENSHOTS)
    parser.add_argument("--require-screenshots", action="store_true",
                        help="Fail if the phone screenshot packet is absent")
    args = parser.parse_args()
    check_text()
    check_png("icon.png", (512, 512), "RGBA", 1024 * 1024)
    check_png("featureGraphic.png", (1024, 500), "RGB")
    check_phone_screenshots(args.screenshots_dir, args.require_screenshots)


if __name__ == "__main__":
    main()
