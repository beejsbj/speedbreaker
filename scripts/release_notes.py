#!/usr/bin/env python3
"""Compose GitHub release notes from a CHANGELOG section plus install and checksum details."""
import argparse
from pathlib import Path
import re
import sys

INSTALL = ("Download **Speedbreaker-{version}.apk** under Assets. Android 8.0+; version code **{code}**. "
           "Same release signing identity. Play Protect may warn about an unfamiliar developer; "
           "choose *More details → Install anyway*.")
LINKS = ("[Setup](https://github.com/beejsbj/speedbreaker/blob/main/docs/getting-started.md) · "
         "[Privacy](https://github.com/beejsbj/speedbreaker/blob/main/docs/privacy.md)")


def changelog_section(changelog, version, require_dated=False):
    """Body of `## VERSION — ...`, without the post-release "Version code: ..." bookkeeping line."""
    heading = re.search(rf"^## {re.escape(version)} — (.+)$", changelog, re.M)
    if not heading:
        raise ValueError(f"CHANGELOG has no section for {version}")
    if require_dated and heading.group(1).strip().lower() == "unreleased":
        raise ValueError(f"CHANGELOG still lists {version} as unreleased; date it before tagging")
    following = re.search(r"^## ", changelog[heading.end():], re.M)
    end = heading.end() + following.start() if following else len(changelog)
    body = re.sub(r"^Version code:.*\n?", "", changelog[heading.end():end], flags=re.M).strip()
    if not body:
        raise ValueError(f"CHANGELOG section for {version} has no release notes")
    return body


def compose(version, version_code, commit, sha256, section):
    return (f"{section}\n\n{INSTALL.format(version=version, code=version_code)}\n\n"
            f"Source: `{commit[:7]}`.\n\nAPK SHA-256:\n```\n{sha256}\n```\n\n{LINKS}\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument("--version-code", required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--sha256", required=True)
    parser.add_argument("--changelog", type=Path, default=Path("CHANGELOG.md"))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--require-dated", action="store_true", help="Refuse an 'unreleased' heading (publishing)")
    args = parser.parse_args()
    try:
        section = changelog_section(args.changelog.read_text(), args.version, args.require_dated)
    except ValueError as error:
        print(f"Refused: {error}", file=sys.stderr)
        sys.exit(1)
    args.output.write_text(compose(args.version, args.version_code, args.commit, args.sha256, section))


if __name__ == "__main__":
    main()
