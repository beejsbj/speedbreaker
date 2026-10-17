#!/usr/bin/env python3
"""Start a release: set versionName, increment versionCode, and open an unreleased CHANGELOG section.

    python3 scripts/bump_version.py 1.0.4

Edit the CHANGELOG notes, replace "unreleased" with the date, merge, then tag vX.Y.Z.
"""
import argparse
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
CODE = re.compile(r"^(\s*versionCode\s*=\s*)(\d+)\s*$", re.M)
NAME = re.compile(r'^(\s*versionName\s*=\s*")([^"]+)(")\s*$', re.M)


def triple(version):
    match = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)", version)
    if not match:
        raise ValueError(f"Version must look like 1.2.3, not {version!r}")
    return tuple(map(int, match.groups()))


def bump_gradle(text, version):
    """Return (new text, new versionCode); the version must be greater than the current one."""
    code, name = CODE.search(text), NAME.search(text)
    if not code or not name:
        raise ValueError("app/build.gradle.kts must declare versionCode and versionName")
    if triple(version) <= triple(name.group(2)):
        raise ValueError(f"{version} is not newer than the current {name.group(2)}")
    new_code = int(code.group(2)) + 1
    text = CODE.sub(lambda m: f"{m.group(1)}{new_code}", text, count=1)
    return NAME.sub(lambda m: f"{m.group(1)}{version}{m.group(3)}", text, count=1), new_code


def open_changelog(text, version, version_code):
    """Ensure `## VERSION — unreleased` exists above the newest section."""
    existing = re.search(rf"^## {re.escape(version)} — (.+)$", text, re.M)
    if existing:
        if existing.group(1).strip().lower() != "unreleased":
            raise ValueError(f"CHANGELOG already has a dated section for {version}")
        return text
    section = f"## {version} — unreleased\n\nVersion code: **{version_code}**.\n\n"
    first = re.search(r"^## ", text, re.M)
    if not first:
        return text.rstrip("\n") + "\n\n" + section.rstrip("\n") + "\n"
    return text[:first.start()] + section + text[first.start():]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("version", help="New versionName, e.g. 1.0.4")
    parser.add_argument("--root", type=Path, default=ROOT)
    args = parser.parse_args()
    gradle, changelog = args.root / "app/build.gradle.kts", args.root / "CHANGELOG.md"
    try:
        new_gradle, code = bump_gradle(gradle.read_text(), args.version)
        new_changelog = open_changelog(changelog.read_text(), args.version, code)
    except ValueError as error:
        print(f"Refused: {error}", file=sys.stderr)
        sys.exit(1)
    gradle.write_text(new_gradle)
    changelog.write_text(new_changelog)
    print(f"versionName {args.version}, versionCode {code}. Fill in the CHANGELOG section, then open a PR.")


if __name__ == "__main__":
    main()
