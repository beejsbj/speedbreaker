#!/usr/bin/env python3
"""Sign the community release APK with the legacy and release keys, verify it, and write its receipt.

Shared by scripts/package_release.py (local signing host) and
.github/workflows/release.yml (the approval-gated `release` environment) so the two
paths cannot drift. Callers apply the store policy before any credential is read; the
release password reaches apksigner only through SPEEDBREAKER_RELEASE_PASSWORD.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "dev.burooj.speedbreaker"
# Both signing paths use this apksigner; a different version could pad the APK differently.
BUILD_TOOLS = "35.0.0"
LINEAGE = ROOT / "signing/speedbreaker.lineage"
ROTATION_MIN_SDK = 28
LEGACY_ALIAS = "androiddebugkey"
RELEASE_ALIAS = "speedbreaker"
# Public certificate fingerprints (see signing/speedbreaker.lineage): Android 8 sees the
# original key, Android 9 and newer the release key.
LEGACY_CERT_SHA256 = "8063da543d9e65ccf8ec4267e0710439f67af51d4990720f0ae36bef236a98ca"
RELEASE_CERT_SHA256 = "fe3fcd3e893fe89d97aba6e25b429e9140455846423c2b42c44bfb4fcc320272"
SDK_RANGES = (("26", "27", LEGACY_CERT_SHA256), ("28", "36", RELEASE_CERT_SHA256))


def run(args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def read_badging(aapt, apk, runner=run):
    """Package identity of an unsigned APK; refuses another package or a debuggable build."""
    badging = runner([str(aapt), "dump", "badging", str(apk)], capture_output=True, text=True).stdout
    package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    if not package or package.group(1) != PACKAGE:
        raise ValueError("Unexpected release package")
    if "application-debuggable" in badging:
        raise ValueError("Refusing to sign a debuggable APK with the release key")
    return {"package": package.group(1), "version_code": int(package.group(2)), "version": package.group(3)}


def signing_command(apksigner, unsigned, output, legacy_keystore, release_keystore, lineage=LINEAGE):
    return [
        str(apksigner), "sign", "--out", str(output),
        "--alignment-preserved",
        "--debuggable-apk-permitted", "false", "--v4-signing-enabled", "false",
        "--lineage", str(lineage), "--rotation-min-sdk-version", str(ROTATION_MIN_SDK),
        "--ks", str(legacy_keystore), "--ks-key-alias", LEGACY_ALIAS, "--ks-pass", "pass:android",
        "--next-signer", "--ks", str(release_keystore), "--ks-key-alias", RELEASE_ALIAS,
        "--ks-pass", "env:SPEEDBREAKER_RELEASE_PASSWORD", "--in", str(unsigned),
    ]


def certificate_digests(text):
    return {digest.lower() for digest in re.findall(r"certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", text)}


def verify_output(runner, args):
    """apksigner verify output holds only public certificate data, so failures may be shown."""
    try:
        return runner(args, capture_output=True, text=True).stdout
    except subprocess.CalledProcessError as error:
        print(error.stdout or "", error.stderr or "", file=sys.stderr)
        raise


def verify_signatures(apksigner, apk, runner=run):
    """Verify every supported SDK range and require the expected signer certificate in each."""
    print(verify_output(runner, [str(apksigner), "verify", "--verbose", "--print-certs", str(apk)]), end="")
    for low, high, expected in SDK_RANGES:
        found = certificate_digests(verify_output(runner, [
            str(apksigner), "verify", "--min-sdk-version", low, "--max-sdk-version", high,
            "--print-certs", str(apk)]))
        if found != {expected}:
            raise ValueError(f"SDK {low}-{high} signer certificate {sorted(found) or 'missing'}, expected {expected}")


def sign_release(apksigner, unsigned, output_dir, package, commit, tree, legacy_keystore, release_keystore,
                 build_receipt=None, env=None, lineage=LINEAGE, runner=run):
    """Sign, verify and write Speedbreaker-X.Y.Z.apk with its .sha256 and .json receipt."""
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    output = output_dir / f"Speedbreaker-{package['version']}.apk"
    if output.exists():
        raise ValueError("Output already exists; use a fresh directory rather than overwrite a signed APK")
    runner(signing_command(apksigner, unsigned, output, legacy_keystore, release_keystore, lineage), env=env)
    verify_signatures(apksigner, output, runner)
    digest = sha256(output)
    output.with_suffix(".apk.sha256").write_text(f"{digest}  {output.name}\n")
    output.with_suffix(".json").write_text(json.dumps({
        "package": package["package"], "version_code": package["version_code"],
        "version": package["version"], "commit": commit, "sha256": digest,
        "debuggable": False, "signing_rotation_min_sdk": ROTATION_MIN_SDK,
        "source_tree": tree, "unsigned_sha256": sha256(unsigned),
        "build_receipt": build_receipt,
    }, indent=2) + "\n")
    return output, digest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--unsigned-apk", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--build-tools", type=Path, required=True, help="Directory holding apksigner and aapt")
    parser.add_argument("--legacy-keystore", type=Path, required=True)
    parser.add_argument("--release-keystore", type=Path, required=True)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--tree", required=True)
    parser.add_argument("--build-receipt", type=Path, help="JSON describing the hosted build that produced the input")
    args = parser.parse_args()
    if not os.environ.get("SPEEDBREAKER_RELEASE_PASSWORD"):
        parser.error("SPEEDBREAKER_RELEASE_PASSWORD must be set in the environment")
    for path in (args.unsigned_apk, args.legacy_keystore, args.release_keystore, LINEAGE):
        if not path.is_file():
            parser.error(f"Missing input: {path}")
    package = read_badging(args.build_tools / "aapt", args.unsigned_apk)
    receipt = json.loads(args.build_receipt.read_text()) if args.build_receipt else None
    output, digest = sign_release(args.build_tools / "apksigner", args.unsigned_apk, args.output_dir, package,
                                  args.commit, args.tree, args.legacy_keystore, args.release_keystore,
                                  build_receipt=receipt, env=os.environ.copy())
    print(f"Signed: {output}\nSHA-256: {digest}")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError:
        print("Signing command failed; credential-bearing output suppressed.", file=sys.stderr)
        sys.exit(1)
    except ValueError as error:
        print(f"Refused: {error}", file=sys.stderr)
        sys.exit(1)
