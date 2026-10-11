#!/usr/bin/env python3
"""Verify the immutable 1.0.1 APK against a clean source rebuild, without keys."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import zipfile

PUBLISHED_SHA256 = "5ca9948dbe730d110f8fd9def95094bb13f9b5ba44c1a51649fc679efb62a966"


def entries(path):
    with zipfile.ZipFile(path) as archive:
        return {item.filename: hashlib.sha256(archive.read(item)).hexdigest()
                for item in archive.infolist() if not item.filename.startswith("META-INF/")}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("signed", type=Path)
    parser.add_argument("rebuilt", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    signed_digest = hashlib.sha256(args.signed.read_bytes()).hexdigest()
    if signed_digest != PUBLISHED_SHA256:
        parser.error("Published APK checksum mismatch; do not compare an unexpected artifact")
    first, second = entries(args.signed), entries(args.rebuilt)
    different = sorted(name for name in first.keys() | second.keys()
                       if first.get(name) != second.get(name))
    result = subprocess.run(["apksigcopier", "compare", "--unsigned", str(args.signed),
                             str(args.rebuilt)], capture_output=True, text=True)
    receipt = {
        "published_sha256": signed_digest,
        "source_commit": "3f90ca53e420fb308dcbe48030566f8824ad1688",
        "rebuilt_sha256": hashlib.sha256(args.rebuilt.read_bytes()).hexdigest(),
        "different_payload_entries_excluding_meta_inf": different,
        "signature_copy_verified": result.returncode == 0,
        "copier_stdout": result.stdout,
        "copier_stderr": result.stderr,
        "scope": "clean hosted rebuild; not an F-Droid buildserver run",
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(receipt, indent=2) + "\n")
    print(json.dumps(receipt, indent=2))
    raise SystemExit(result.returncode)


if __name__ == "__main__":
    main()
