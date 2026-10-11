#!/usr/bin/env python3
"""Build and sign the shareable APK without putting passwords in arguments."""
import argparse
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def run(args, **kwargs):
    return subprocess.run(args, check=True, cwd=ROOT, **kwargs)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--skip-build", action="store_true", help="Sign the already built release APK")
    parser.add_argument("--unsigned-apk", type=Path, help="Verified hosted community APK (requires --skip-build)")
    parser.add_argument("--build-receipt", type=Path, help="Input receipt from fetch_store_artifacts.py (required with --skip-build)")
    parser.add_argument("--password-secret", help="Bitwarden secret ID, read through bjs-bws on bjslab")
    parser.add_argument("--output-dir", type=Path, default=ROOT / "dist")
    args = parser.parse_args()
    args.output_dir = args.output_dir.expanduser().resolve()
    sdk = Path(os.environ.get("ANDROID_SDK_ROOT", "/usr/lib/android-sdk")).expanduser().resolve()
    build_tools = sdk / "build-tools" / "35.0.0"
    for tool in ("apksigner", "aapt"):
        if not (build_tools / tool).is_file():
            parser.error(f"Install Android build-tools 35.0.0 in {sdk}")
    signing = Path(os.environ.get("SPEEDBREAKER_SIGNING_DIR", str(Path.home() / ".local/share/speedbreaker-signing"))).expanduser().resolve()
    release_key = signing / "release.jks"
    legacy_key = Path(os.environ.get("SPEEDBREAKER_LEGACY_KEYSTORE", str(Path.home() / ".android/debug.keystore"))).expanduser().resolve()
    lineage = ROOT / "signing/speedbreaker.lineage"
    if not all(path.is_file() for path in (release_key, legacy_key, lineage)):
        parser.error("Restore the original release and legacy keys before signing; never generate replacements for an update")
    changed = run(["git", "status", "--porcelain", "--", "app", "gradle", "scripts", "signing", "build.gradle.kts", "settings.gradle.kts", "gradle.properties"], capture_output=True, text=True).stdout
    if changed:
        parser.error("Commit source and packaging changes before building a release")
    commit = run(["git", "rev-parse", "HEAD"], capture_output=True, text=True).stdout.strip()
    tree = run(["git", "rev-parse", "HEAD^{tree}"], capture_output=True, text=True).stdout.strip()
    if (args.unsigned_apk or args.build_receipt) and not args.skip_build:
        parser.error("Hosted inputs require --skip-build")
    if args.skip_build and not (args.unsigned_apk and args.build_receipt):
        parser.error("--skip-build requires --unsigned-apk and --build-receipt; fetch verified CI inputs first")
    if not args.skip_build:
        run([str(ROOT / "gradlew"), ":app:assembleCommunityRelease", "--no-daemon", "--no-configuration-cache", "--max-workers=1", "-Dorg.gradle.jvmargs=-Xmx1g -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8"])
    unsigned = args.unsigned_apk.expanduser().resolve() if args.unsigned_apk else ROOT / "app/build/outputs/apk/community/release/app-community-release-unsigned.apk"
    if not unsigned.is_file():
        parser.error("Community release APK missing; run assembleCommunityRelease first")
    build_receipt = None
    if args.skip_build:
        build_receipt = json.loads(args.build_receipt.read_text())
        expected = {"repository": "beejsbj/speedbreaker", "source_commit": commit,
                    "source_tree": tree, "channel": "community", "kind": "apk",
                    "input_sha256": hashlib.sha256(unsigned.read_bytes()).hexdigest()}
        if any(build_receipt.get(key) != value for key, value in expected.items()):
            parser.error("Hosted input receipt does not match this committed source and APK")
        host = json.loads(run(["gh", "api", f"repos/beejsbj/speedbreaker/actions/runs/{int(build_receipt['run_id'])}"],
                              capture_output=True, text=True).stdout)
        if host.get("head_sha") != commit or host.get("conclusion") != "success" or host.get("status") != "completed":
            parser.error("Hosted build no longer has successful evidence for this commit")
    badging = run([str(build_tools / "aapt"), "dump", "badging", str(unsigned)], capture_output=True, text=True).stdout
    package = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    if not package or package.group(1) != "dev.burooj.speedbreaker":
        parser.error("Unexpected release package")
    if "application-debuggable" in badging:
        parser.error("Refusing to sign a debuggable APK with the release key")
    password = os.environ.get("SPEEDBREAKER_RELEASE_PASSWORD")
    if not password and args.password_secret:
        response = run(["bjs-bws", "secret", "get", args.password_secret], capture_output=True, text=True)
        password = json.loads(response.stdout)["value"]
    if not password:
        password = getpass.getpass("Release keystore password: ")
    env = os.environ.copy()
    env["SPEEDBREAKER_RELEASE_PASSWORD"] = password
    args.output_dir.mkdir(parents=True, exist_ok=True)
    output = args.output_dir / f"Speedbreaker-{package.group(3)}.apk"
    if output.exists():
        parser.error("Output already exists; use a fresh directory rather than overwrite a signed APK")
    run([
        str(build_tools / "apksigner"), "sign", "--out", str(output),
        "--alignment-preserved",
        "--debuggable-apk-permitted", "false", "--v4-signing-enabled", "false",
        "--lineage", str(lineage), "--rotation-min-sdk-version", "28",
        "--ks", str(legacy_key), "--ks-key-alias", "androiddebugkey", "--ks-pass", "pass:android",
        "--next-signer", "--ks", str(release_key), "--ks-key-alias", "speedbreaker",
        "--ks-pass", "env:SPEEDBREAKER_RELEASE_PASSWORD", "--in", str(unsigned),
    ], env=env)
    run([str(build_tools / "apksigner"), "verify", "--verbose", "--print-certs", str(output)])
    run([str(build_tools / "apksigner"), "verify", "--min-sdk-version", "26", "--max-sdk-version", "27", str(output)])
    run([str(build_tools / "apksigner"), "verify", "--min-sdk-version", "28", "--max-sdk-version", "36", str(output)])
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    checksum = output.with_suffix(".apk.sha256")
    checksum.write_text(f"{digest}  {output.name}\n")
    output.with_suffix(".json").write_text(json.dumps({
        "package": package.group(1), "version_code": int(package.group(2)),
        "version": package.group(3), "commit": commit, "sha256": digest,
        "debuggable": False, "signing_rotation_min_sdk": 28,
        "source_tree": tree, "unsigned_sha256": hashlib.sha256(unsigned.read_bytes()).hexdigest(),
        "build_receipt": build_receipt,
    }, indent=2) + "\n")
    print(f"Share: {output}\nSHA-256: {digest}")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError:
        print("Release command failed; credential-bearing output suppressed.", file=sys.stderr)
        sys.exit(1)
