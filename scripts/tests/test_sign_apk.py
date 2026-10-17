"""Local and CI signing share one core: it must pin the keys, the lineage and both SDK ranges."""
import contextlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

SCRIPTS = Path(__file__).parents[1]
sys.path.insert(0, str(SCRIPTS))
import sign_apk  # noqa: E402

BADGING = "package: name='dev.burooj.speedbreaker' versionCode='7' versionName='1.0.3'\n"


def certs(digest):
    return f"Verifies\nSigner #1 certificate SHA-256 digest: {digest}\nSigner #1 key size (bits): 2048\n"


class FakeApksigner:
    """Answers `verify` per SDK range; `sign` writes the output the way apksigner would."""

    def __init__(self, legacy=sign_apk.LEGACY_CERT_SHA256, release=sign_apk.RELEASE_CERT_SHA256):
        self.legacy, self.release, self.calls = legacy, release, []

    def __call__(self, args, **kwargs):
        self.calls.append(args)
        if args[1] == "sign":
            Path(args[args.index("--out") + 1]).write_bytes(b"signed")
            return subprocess.CompletedProcess(args, 0, "", "")
        legacy = "--max-sdk-version" in args and args[args.index("--max-sdk-version") + 1] == "27"
        digest = self.legacy if legacy else self.release
        return subprocess.CompletedProcess(args, 0, certs(digest), "")


class SigningCoreTest(unittest.TestCase):
    def setUp(self):
        stdout = contextlib.redirect_stdout(io.StringIO())
        stdout.__enter__()
        self.addCleanup(stdout.__exit__, None, None, None)

    def test_signing_command_keeps_lineage_rotation_and_keeps_password_out_of_arguments(self):
        command = sign_apk.signing_command("apksigner", "in.apk", "out.apk", "legacy.jks", "release.jks")
        self.assertEqual(command[command.index("--rotation-min-sdk-version") + 1], "28")
        self.assertEqual(command[command.index("--lineage") + 1], str(sign_apk.LINEAGE))
        self.assertLess(command.index("legacy.jks"), command.index("--next-signer"))
        self.assertLess(command.index("--next-signer"), command.index("release.jks"))
        self.assertIn("env:SPEEDBREAKER_RELEASE_PASSWORD", command)
        self.assertEqual(command[command.index("--debuggable-apk-permitted") + 1], "false")
        self.assertEqual(command[command.index("--v4-signing-enabled") + 1], "false")

    def test_pinned_certificates_match_the_public_lineage(self):
        self.assertEqual(sign_apk.LEGACY_CERT_SHA256, "8063da543d9e65ccf8ec4267e0710439f67af51d4990720f0ae36bef236a98ca")
        self.assertEqual(sign_apk.RELEASE_CERT_SHA256, "fe3fcd3e893fe89d97aba6e25b429e9140455846423c2b42c44bfb4fcc320272")
        self.assertEqual([(low, high) for low, high, _ in sign_apk.SDK_RANGES], [("26", "27"), ("28", "36")])

    def test_certificate_digests_ignore_other_hashes(self):
        text = certs("AB" * 32) + "Signer #1 public key SHA-256 digest: " + "cd" * 32 + "\n"
        self.assertEqual(sign_apk.certificate_digests(text), {"ab" * 32})

    def test_verification_accepts_the_expected_signer_per_range(self):
        sign_apk.verify_signatures("apksigner", "a.apk", FakeApksigner())

    def test_verification_rejects_a_wrong_signer_in_either_range(self):
        for fake in (FakeApksigner(legacy="0" * 64), FakeApksigner(release="1" * 64)):
            with self.assertRaises(ValueError):
                sign_apk.verify_signatures("apksigner", "a.apk", fake)

    def test_badging_rejects_debuggable_and_foreign_packages(self):
        def badging(text):
            return lambda args, **kwargs: subprocess.CompletedProcess(args, 0, text, "")
        self.assertEqual(sign_apk.read_badging("aapt", "a.apk", badging(BADGING)),
                         {"package": "dev.burooj.speedbreaker", "version_code": 7, "version": "1.0.3"})
        with self.assertRaises(ValueError):
            sign_apk.read_badging("aapt", "a.apk", badging(BADGING + "application-debuggable\n"))
        with self.assertRaises(ValueError):
            sign_apk.read_badging("aapt", "a.apk", badging(BADGING.replace("burooj", "other")))

    def test_sign_release_writes_apk_checksum_and_receipt_and_never_overwrites(self):
        with tempfile.TemporaryDirectory() as temporary:
            unsigned = Path(temporary) / "unsigned.apk"
            unsigned.write_bytes(b"unsigned")
            package = {"package": "dev.burooj.speedbreaker", "version_code": 7, "version": "1.0.3"}
            out = Path(temporary) / "dist"
            apk, digest = sign_apk.sign_release("apksigner", unsigned, out, package, "c" * 40, "t" * 40,
                                                "legacy.jks", "release.jks", build_receipt={"run_id": 1},
                                                runner=FakeApksigner())
            self.assertEqual(apk.name, "Speedbreaker-1.0.3.apk")
            self.assertEqual((out / "Speedbreaker-1.0.3.apk.sha256").read_text(), f"{digest}  Speedbreaker-1.0.3.apk\n")
            receipt = json.loads((out / "Speedbreaker-1.0.3.json").read_text())
            self.assertEqual(sorted(receipt), sorted([
                "package", "version_code", "version", "commit", "sha256", "debuggable", "signing_rotation_min_sdk",
                "source_tree", "unsigned_sha256", "build_receipt"]))
            self.assertEqual((receipt["sha256"], receipt["signing_rotation_min_sdk"], receipt["build_receipt"]),
                             (digest, 28, {"run_id": 1}))
            with self.assertRaises(ValueError):
                sign_apk.sign_release("apksigner", unsigned, out, package, "c", "t", "l", "r", runner=FakeApksigner())

    def test_local_script_signs_through_the_shared_core(self):
        source = (SCRIPTS / "package_release.py").read_text()
        self.assertIn("sign_release(", source)
        self.assertNotIn("--lineage", source)
        self.assertNotIn('"sign"', source)
