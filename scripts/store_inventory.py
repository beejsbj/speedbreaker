#!/usr/bin/env python3
"""Collect release POM evidence and verify an unsigned APK's final manifest."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET


def digest(data):
    return hashlib.sha256(data).hexdigest()


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")


def run(*args):
    return subprocess.check_output(args, text=True).strip()


def pom_evidence(coordinate, root, cache, depth=0):
    group, name, version = (coordinate[k] for k in ("group", "name", "version"))
    relative = "/".join((group.replace(".", "/"), name, version, f"{name}-{version}.pom"))
    candidates = [
        ("https://dl.google.com/dl/android/maven2/" + relative, "google"),
        ("https://repo.maven.apache.org/maven2/" + relative, "central"),
    ]
    cached = list((cache / group / name / version).glob("*/" + f"{name}-{version}.pom")) if cache.exists() else []
    data = None
    source = None
    errors = []
    for path in cached:
        data, source = path.read_bytes(), "Gradle cache"
        break
    if data is None:
        for url, _ in candidates:
            try:
                with urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "Speedbreaker-inventory/1"}), timeout=12) as response:
                    data, source = response.read(), url
                break
            except (OSError, urllib.error.HTTPError) as exc:
                errors.append(f"{url}: {type(exc).__name__}")
    stem = f"{group}-{name}-{version}".replace("/", "_")
    if data is None:
        return {"component": coordinate, "status": "pom_missing", "attemptedUrls": [url for url, _ in candidates], "errors": errors}
    target = root / ("parents" if depth else "poms") / (stem + ".pom")
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    result = {"component": coordinate, "status": "found", "pomFile": str(target.relative_to(root)),
              "pomSha256": digest(data), "pomSource": source, "candidateUrls": [url for url, _ in candidates], "licenses": []}
    try:
        doc = ET.fromstring(data)
        namespace = doc.tag[1:].split("}", 1)[0] if doc.tag.startswith("{") else ""
        ns = {"m": namespace}
        licenses = doc.findall("m:licenses/m:license", ns)
        if not licenses:
            parent = doc.find("m:parent", ns)
            if parent is not None:
                result["parent"] = {key: parent.findtext("m:" + key, default="", namespaces=ns) for key in ("groupId", "artifactId", "version")}
                if depth < 3 and all(result["parent"].values()):
                    parent_coordinate = {"group": result["parent"]["groupId"],
                                         "name": result["parent"]["artifactId"],
                                         "version": result["parent"]["version"]}
                    result["parentEvidence"] = pom_evidence(parent_coordinate, root, cache, depth + 1)
            result["status"] = "no_direct_pom_license"
        for license_node in licenses:
            result["licenses"].append({"name": license_node.findtext("m:name", default="", namespaces=ns),
                                       "url": license_node.findtext("m:url", default="", namespaces=ns),
                                       "distribution": license_node.findtext("m:distribution", default="", namespaces=ns)})
    except ET.ParseError as exc:
        result["status"] = "invalid_pom"
        result["parseError"] = str(exc)
    return result


def collect(args):
    root = Path(args.output)
    inventory = json.loads(Path(args.dependencies).read_text())
    coordinates = {tuple(artifact["component"][key] for key in ("group", "name", "version"))
                   for artifact in inventory["artifacts"] if all(key in artifact["component"] for key in ("group", "name", "version"))}
    payment_artifacts = [artifact for artifact in inventory["artifacts"]
                         if artifact["component"].get("group") in {
                             "com.android.billingclient", "com.google.android.datatransport", "com.google.firebase"}]
    if payment_artifacts:
        raise ValueError("Direct-tip runtime contains a payment or diagnostics transport SDK")
    # Platform/BOM nodes have no runtime file, but are part of the resolved graph.
    coordinates.update(tuple(node["component"][key] for key in ("group", "name", "version"))
                       for node in inventory["graph"] if all(key in node["component"] for key in ("group", "name", "version")))
    cache = Path.home() / ".gradle/caches/modules-2/files-2.1"
    results = [pom_evidence(dict(zip(("group", "name", "version"), coord)), root, cache) for coord in sorted(coordinates)]
    write_json(root / "licenses.json", {"schemaVersion": 1, "channel": inventory["channel"], "components": results})
    urls = set()
    def license_urls(record):
        urls.update(item["url"] for item in record.get("licenses", []) if item["url"])
        if record.get("parentEvidence"):
            license_urls(record["parentEvidence"])
    for result in results:
        license_urls(result)
    texts = []
    for url in sorted(urls):
        entry = {"pomLicenseUrl": url}
        fetch_url = url.replace("http://www.apache.org/", "https://www.apache.org/")
        if not fetch_url.startswith("https://"):
            entry["status"] = "non_https_url_not_fetched"
        else:
            try:
                with urllib.request.urlopen(urllib.request.Request(fetch_url, headers={"User-Agent": "Speedbreaker-inventory/1"}), timeout=12) as response:
                    data = response.read(512 * 1024 + 1)
                    content_type = response.headers.get("Content-Type", "")
                if len(data) > 512 * 1024 or not ("text" in content_type or "html" in content_type):
                    entry["status"] = "non_text_or_too_large"
                else:
                    path = root / "license-texts" / (digest(url.encode()) + ".txt")
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(data)
                    entry.update(status="fetched", fetchedUrl=fetch_url,
                                 file=str(path.relative_to(root)), sha256=digest(data), contentType=content_type)
            except (OSError, urllib.error.HTTPError) as exc:
                entry.update(status="fetch_failed", error=type(exc).__name__)
        texts.append(entry)
    write_json(root / "license-texts.json", {"schemaVersion": 1, "urls": texts})
    print(f"{inventory['channel']}: {len(inventory['artifacts'])} artifacts, {len(coordinates)} Maven components, "
          f"{sum(item['status'] != 'found' for item in results)} POM/license unknowns")


def expected_version_code():
    """The versionCode declared in the app build, so a release bump needs no script edit."""
    build = (Path(__file__).resolve().parent.parent / "app/build.gradle.kts").read_text()
    match = re.search(r"^\s*versionCode\s*=\s*(\d+)", build, re.M)
    assert match, "app/build.gradle.kts declares no versionCode"
    return match.group(1)


def verify(args):
    apk = Path(args.apk)
    aapt = args.aapt
    badging = run(aapt, "dump", "badging", str(apk))
    permissions_text = run(aapt, "dump", "permissions", str(apk))
    xmltree = run(aapt, "dump", "xmltree", str(apk), "AndroidManifest.xml")
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)'", badging, re.M)
    sdk = re.search(r"^sdkVersion:'([^']+)'", badging, re.M)
    target = re.search(r"^targetSdkVersion:'([^']+)'", badging, re.M)
    permissions = sorted(set(re.findall(r"^uses-permission(?:-sdk-23)?: name='([^']+)'", permissions_text, re.M)))
    assert package and sdk and target, "aapt badging omitted package or SDK metadata"
    billing = "com.android.vending.BILLING"
    # Android's binary XML rendering uses android:debuggable(0x0101000f).
    debuggable = bool(re.search(r"android:debuggable(?:\([^)]*\))?=.*(?:0xffffffff|true)", xmltree, re.I))
    violations = []
    expected = ("dev.burooj.speedbreaker", expected_version_code(), "26", "36")
    if (package.group(1), package.group(2), sdk.group(1), target.group(1)) != expected:
        violations.append("package/version/SDK mismatch")
    if "android.permission.INTERNET" in permissions:
        violations.append("INTERNET permission present")
    if billing in permissions:
        violations.append("Billing permission present in direct-tip build")
    if "android.permission.ACCESS_NETWORK_STATE" in permissions:
        violations.append("Network-state permission present in offline build")
    if debuggable:
        violations.append("debuggable APK")
    manifest = {"channel": args.channel, "package": package.group(1), "versionCode": int(package.group(2)),
                "minSdk": int(sdk.group(1)), "targetSdk": int(target.group(1)), "permissions": permissions,
                "debuggable": debuggable, "policyViolations": violations,
                "inspectionTool": Path(aapt).name, "apkSha256": digest(apk.read_bytes())}
    root = Path(args.output)
    write_json(root / "manifest.json", manifest)
    (root / "manifest-aapt.txt").write_text("BADGING\n" + badging + "\nPERMISSIONS\n" + permissions_text + "\nXMLTREE\n" + xmltree + "\n")
    head = run("git", "rev-parse", "HEAD")
    tree = run("git", "rev-parse", "HEAD^{tree}")
    event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text()) if os.environ.get("GITHUB_EVENT_PATH") else {}
    pr = event.get("pull_request", {})
    artifact_hashes = {"apk": manifest["apkSha256"]}
    if args.aab:
        artifact_hashes["aab"] = digest(Path(args.aab).read_bytes())
    provenance = {"schemaVersion": 1, "sourceHead": head, "sourceTree": tree,
                  "githubRunId": os.environ.get("GITHUB_RUN_ID"), "githubRunAttempt": os.environ.get("GITHUB_RUN_ATTEMPT"),
                  "githubEvent": os.environ.get("GITHUB_EVENT_NAME"), "githubHeadSha": os.environ.get("GITHUB_SHA"),
                  "pullRequestHeadSha": pr.get("head", {}).get("sha"), "artifactSha256": artifact_hashes,
                  "channel": args.channel, "signed": False}
    write_json(root / "provenance.json", provenance)
    if violations:
        raise ValueError(f"{args.channel} final APK policy failed: {', '.join(violations)}; see manifest.json")
    print(f"{args.channel}: manifest verified; APK SHA-256 {manifest['apkSha256']}")


def self_test(_args):
    sample = b'<project xmlns="http://maven.apache.org/POM/4.0.0"><licenses><license><name>Test</name><url>https://example.org/license</url></license></licenses></project>'
    assert ET.fromstring(sample).findtext("m:licenses/m:license/m:name", namespaces={"m": "http://maven.apache.org/POM/4.0.0"}) == "Test"
    plain = b"<project><licenses><license><name>Apache-2.0</name></license></licenses></project>"
    assert ET.fromstring(plain).findtext("m:licenses/m:license/m:name", namespaces={"m": ""}) == "Apache-2.0"
    assert digest(b"abc") == "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    print("self-test passed")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="command", required=True)
    p = sub.add_parser("collect")
    p.add_argument("--dependencies", required=True)
    p.add_argument("--output", required=True)
    p.set_defaults(action=collect)
    p = sub.add_parser("verify")
    p.add_argument("--channel", choices=("community", "play"), required=True)
    p.add_argument("--apk", required=True)
    p.add_argument("--aab")
    p.add_argument("--aapt", required=True)
    p.add_argument("--output", required=True)
    p.set_defaults(action=verify)
    sub.add_parser("self-test").set_defaults(action=self_test)
    args = parser.parse_args()
    args.action(args)


if __name__ == "__main__":
    main()
