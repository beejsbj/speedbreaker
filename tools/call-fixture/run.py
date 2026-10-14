#!/usr/bin/env python3
"""Destructive only to the two test packages on an explicitly selected disposable emulator."""

import argparse
import hashlib
import json
from pathlib import Path
import queue
import shlex
import sqlite3
import subprocess
import sys
import threading
import time

PRODUCT = "dev.burooj.speedbreaker"
FIXTURE = "dev.burooj.speedbreaker.fixture"
SERVICE = PRODUCT + "/.observation.SpeedbreakerService"
NUMBER = "15555215554"  # Emulator modem identifier, never passed to a real dial action.
ROOT = Path(__file__).resolve().parents[2]


def seed_database(path):
    """Room v1 validates this prepackaged schema and creates its own identity metadata."""
    path.unlink(missing_ok=True)
    settings = {
        "consent": True, "breath": 30, "redirects": [], "schedule": None,
        "apps": {FIXTURE: {"continuous": None, "schedule": None, "redirects": None}},
    }
    with sqlite3.connect(path) as db:
        db.execute("CREATE TABLE speedbreaker_state (`key` TEXT NOT NULL PRIMARY KEY, `value` TEXT NOT NULL)")
        db.executemany("INSERT INTO speedbreaker_state VALUES (?, ?)",
                       [("settings", json.dumps(settings)), ("pauses", "{}")])
        db.execute("PRAGMA user_version=1")


class Harness:
    def __init__(self, args):
        self.args = args
        self.adb = ["adb", "-s", args.serial]
        self.output = args.output.resolve()
        self.output.mkdir(parents=True, exist_ok=True)

    def command(self, *args, check=True, binary=False, stdin=None):
        result = subprocess.run(self.adb + list(args), input=stdin, capture_output=True,
                                text=not binary, timeout=30)
        if check and result.returncode:
            raise RuntimeError(f"adb {shlex.join(args)} failed: {result.stderr!r} {result.stdout!r}")
        return result.stdout if binary else result.stdout.strip()

    def shell(self, *args, **kwargs):
        # adb shell joins its arguments; pass one correctly quoted shell command.
        return self.command("shell", shlex.join(args), **kwargs)

    def gsm(self, action):
        output = self.command("emu", "gsm", action, NUMBER)
        if "KO:" in output or "OK" not in output:
            raise RuntimeError(f"Emulator gsm {action} failed: {output}")

    def validate_emulator(self):
        if not self.args.reset_test_packages or not self.args.serial.startswith("emulator-"):
            raise RuntimeError("Use --reset-test-packages and an explicit emulator-NNNN serial")
        if self.shell("getprop", "ro.kernel.qemu") != "1":
            raise RuntimeError("Refusing to run outside an Android emulator")
        if int(self.shell("getprop", "ro.build.version.sdk")) < 33:
            raise RuntimeError("This SystemUI heads-up fixture requires API 33+ (CI uses 35)")
        locale = self.shell("getprop", "persist.sys.locale")
        if locale and not locale.startswith("en"):
            raise RuntimeError("Use an English emulator image; call action labels are asserted")
        calls = self.command("emu", "gsm", "list")
        if "inbound" in calls or "outbound" in calls:
            raise RuntimeError("Emulator already has a simulated call; use a fresh AVD")

    def prepare(self, directory):
        self.shell("settings", "put", "secure", "enabled_accessibility_services", "null")
        self.shell("settings", "put", "secure", "accessibility_enabled", "0")
        for package in (PRODUCT, FIXTURE):
            self.shell("am", "force-stop", package)
            if self.shell("pm", "clear", package) != "Success":
                raise RuntimeError(f"Could not clear {package}")
        seed = directory / "speedbreaker.db"
        seed_database(seed)
        self.shell("run-as", PRODUCT, "mkdir", "-p", "databases")
        # Stream directly into app-owned storage, with no world-readable database or sqlite3 CLI.
        script = shlex.join(["run-as", PRODUCT, "sh", "-c", "cat > databases/speedbreaker.db"])
        self.command("exec-in", script, binary=True, stdin=seed.read_bytes())
        for package in (PRODUCT, FIXTURE):
            grant = self.shell("pm", "grant", package, "android.permission.READ_PHONE_STATE", check=False)
            permissions = self.shell("dumpsys", "package", package)
            granted = "android.permission.READ_PHONE_STATE: granted=true" in permissions
            if not granted and not (package == PRODUCT and self.args.baseline_without_phone_permission):
                raise RuntimeError(f"READ_PHONE_STATE not granted to {package}: {grant}")
        self.shell("cmd", "notification", "allow_dnd", FIXTURE)
        self.shell("input", "keyevent", "KEYCODE_WAKEUP")
        self.shell("wm", "dismiss-keyguard")
        self.shell("cmd", "notification", "set_dnd", "off")
        self.shell("settings", "put", "system", "screen_off_timeout", "600000")
        self.shell("am", "start", "-W", "-n", PRODUCT + "/.MainActivity")
        self.shell("input", "keyevent", "KEYCODE_HOME")
        self.shell("settings", "put", "secure", "enabled_accessibility_services", SERVICE)
        self.shell("settings", "put", "secure", "accessibility_enabled", "1")
        # Binding happens asynchronously. The positive overlay precondition is the final proof.
        time.sleep(2)

    def collect(self, directory):
        for name in ("trace.jsonl", "locked.png", "systemui-incoming.png", "answered.png",
                     "reentry.png", "navigation-released.png", "failure.png"):
            if self.shell("run-as", FIXTURE, "ls", "files/" + name, check=False):
                data = self.command("exec-out", "run-as", FIXTURE, "cat", "files/" + name,
                                    binary=True, check=False)
                if data:
                    (directory / name).write_bytes(data)
        for service in ("window", "accessibility", "telecom", "notification"):
            (directory / (service + ".txt")).write_text(self.shell("dumpsys", service, check=False))
        (directory / "logcat.txt").write_text(self.command("logcat", "-d", "-t", "3000", check=False))

    def run_case(self, scenario):
        directory = self.output / scenario
        directory.mkdir(exist_ok=True)
        self.prepare(directory)
        self.command("logcat", "-c")
        command = self.adb + ["shell", "am", "instrument", "-w", "-r", "-e", "scenario", scenario,
                              FIXTURE + "/.CallRegression"]
        process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   text=True, bufsize=1)
        lines = queue.Queue()

        def read_output():
            for line in process.stdout:
                lines.put(line)
            lines.put(None)

        reader = threading.Thread(target=read_output, daemon=True)
        reader.start()
        transcript = []
        host_events = []
        deadline = time.monotonic() + 150
        injected = False
        warm_injected = False
        try:
            while True:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise TimeoutError("Instrumentation exceeded 150 seconds including warmup")
                try:
                    line = lines.get(timeout=min(remaining, 1))
                except queue.Empty:
                    continue
                if line is None:
                    break
                transcript.append(line)
                print(line, end="", flush=True)
                if line.strip() == "SB_WARM_CALL_READY":
                    if warm_injected:
                        raise RuntimeError("Duplicate warmup call request")
                    self.gsm("call")
                    warm_injected = True
                elif line.strip() == "SB_WARM_CANCEL_READY":
                    self.gsm("cancel")
                elif line.strip() == "SB_CALL_READY":
                    if injected:
                        raise RuntimeError("Duplicate call injection request")
                    host_events.append({"event": "gsm_call_sent", "monotonic": time.monotonic()})
                    self.gsm("call")
                    injected = True
                elif line.strip() == "SB_CANCEL_READY":
                    self.gsm("cancel")
                    host_events.append({"event": "gsm_cancel_sent", "monotonic": time.monotonic()})
            process.wait(timeout=5)
            text = "".join(transcript)
            if process.returncode != 0 or "INSTRUMENTATION_RESULT: result=PASS" not in text:
                raise AssertionError(f"{scenario} failed; see {directory}")
            if scenario != "navigation" and not injected:
                raise AssertionError("Call scenario passed without host injection")
        finally:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=5)
                self.shell("am", "force-stop", FIXTURE, check=False)
            (directory / "instrumentation.txt").write_text("".join(transcript))
            (directory / "host-events.json").write_text(json.dumps(host_events, indent=2))
            self.collect(directory)
            if injected or warm_injected:
                self.command("emu", "gsm", "cancel", NUMBER, check=False)

    def run(self):
        self.validate_emulator()
        for apk in (self.args.app_apk, self.args.fixture_apk):
            self.command("install", "-r", "-t", str(apk.resolve()))
        metadata = {
            "git_head": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
            "serial": self.args.serial,
            "fingerprint": self.shell("getprop", "ro.build.fingerprint"),
            "baseline_without_phone_permission": self.args.baseline_without_phone_permission,
            "app_commit": self.args.app_commit,
            "app_sha256": hashlib.sha256(self.args.app_apk.read_bytes()).hexdigest(),
            "fixture_sha256": hashlib.sha256(self.args.fixture_apk.read_bytes()).hexdigest(),
        }
        (self.output / "environment.json").write_text(json.dumps(metadata, indent=2))
        try:
            scenarios = ["navigation", "ringing", "silent"] if self.args.scenario == "all" else [self.args.scenario]
            failures = []
            for scenario in scenarios:
                try:
                    self.run_case(scenario)
                except Exception as failure:
                    failures.append(f"{scenario}: {failure}")
                    print(failures[-1], file=sys.stderr)
            if failures:
                raise AssertionError("\n".join(failures))
        finally:
            self.shell("settings", "put", "secure", "enabled_accessibility_services", "null", check=False)
            self.shell("settings", "put", "secure", "accessibility_enabled", "0", check=False)
            self.shell("am", "force-stop", PRODUCT, check=False)
            self.command("emu", "gsm", "cancel", NUMBER, check=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--reset-test-packages", action="store_true",
                        help="Confirm this is a disposable emulator; clears both packages and accessibility settings")
    parser.add_argument("--app-apk", type=Path, default=ROOT / "app/build/outputs/apk/community/debug/app-community-debug.apk")
    parser.add_argument("--app-commit", help="Source commit of a separately built app APK, recorded with its SHA-256")
    parser.add_argument("--fixture-apk", type=Path,
                        default=ROOT / "tools/call-fixture/build/outputs/apk/debug/call-fixture-debug.apk")
    parser.add_argument("--output", type=Path, default=ROOT / "build/device-regression")
    parser.add_argument("--scenario", choices=["all", "navigation", "ringing", "silent"], default="all")
    parser.add_argument("--baseline-without-phone-permission", action="store_true",
                        help="Only for proving the pre-fix APK fails; does not relax call assertions")
    Harness(parser.parse_args()).run()


if __name__ == "__main__":
    main()
