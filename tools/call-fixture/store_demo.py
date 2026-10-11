#!/usr/bin/env python3
"""Record an actual first-run policy demo on an explicitly disposable Android emulator."""

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PRODUCT = "dev.burooj.speedbreaker"
FIXTURE = PRODUCT + ".fixture"
ROOT = Path(__file__).resolve().parents[2]


class Demo:
    def __init__(self, args):
        self.args = args
        self.adb = ["adb", "-s", args.serial]
        self.out = args.output.resolve()
        self.out.mkdir(parents=True, exist_ok=True)
        self.events = []
        self.assertions = {}
        self.video = None

    def adb_call(self, *parts, timeout=30, binary=False, check=True):
        result = subprocess.run(self.adb + list(parts), capture_output=True,
                                text=not binary, timeout=timeout)
        if check and result.returncode:
            raise RuntimeError(f"adb {parts!r}: {result.stderr!r} {result.stdout!r}")
        return result.stdout if binary else result.stdout.strip()

    def shell(self, *parts, **kw):
        return self.adb_call("shell", *parts, **kw)

    def mark(self, name, detail=None):
        event = {"at_utc": datetime.now(timezone.utc).isoformat(),
                 "video_seconds": round(time.monotonic() - self.started, 2), "event": name}
        if detail is not None:
            event["detail"] = detail
        self.events.append(event)
        print(json.dumps(event), flush=True)

    def require(self, condition, name):
        self.assertions[name] = bool(condition)
        if not condition:
            raise AssertionError(name)
        self.mark("asserted", name)

    def validate(self):
        self.require(self.args.reset_test_packages and self.args.serial == "emulator-5554",
                     "explicit disposable emulator-5554 and reset flag")
        self.require(self.shell("getprop", "ro.kernel.qemu") == "1", "Android emulator")
        self.require(int(self.shell("getprop", "ro.build.version.sdk")) >= 33, "API 33 or newer")
        locale = self.shell("getprop", "persist.sys.locale")
        self.require(not locale or locale.startswith("en"), "English UI")

    def dump(self):
        # UIAutomator writes a temporary hierarchy file; no app data or settings are changed.
        self.shell("uiautomator", "dump", "/sdcard/sb-store-demo.xml", timeout=20)
        raw = self.adb_call("exec-out", "cat", "/sdcard/sb-store-demo.xml", binary=True)
        self.shell("rm", "/sdcard/sb-store-demo.xml")
        return ET.fromstring(raw)

    @staticmethod
    def label(node):
        return " ".join((node.get("text", ""), node.get("content-desc", ""))).strip()

    def find(self, phrase, timeout=12, exact=False):
        end = time.monotonic() + timeout
        while time.monotonic() < end:
            root = self.dump()
            for node in root.iter("node"):
                label = self.label(node)
                if (label == phrase if exact else phrase.casefold() in label.casefold()):
                    return node, root
            time.sleep(.5)
        raise AssertionError(f"UI did not show {phrase!r}; see last-ui.xml")

    def tap_node(self, node):
        bounds = node.get("bounds", "")
        coords = list(map(int, re.findall(r"\d+", bounds)))
        if len(coords) != 4 or coords[2] <= coords[0] or coords[3] <= coords[1]:
            raise AssertionError(f"No tappable bounds: {ET.tostring(node, encoding='unicode')}")
        self.shell("input", "tap", str((coords[0] + coords[2]) // 2),
                   str((coords[1] + coords[3]) // 2))

    def tap(self, phrase, **kw):
        node, _ = self.find(phrase, **kw)
        self.tap_node(node)
        self.mark("tap", phrase)

    def visible(self, phrase):
        return any(phrase.casefold() in self.label(n).casefold() for n in self.dump().iter("node"))

    def swipe(self, direction="up"):
        if direction == "up":
            self.shell("input", "swipe", "530", "1750", "530", "500", "450")
        else:
            self.shell("input", "swipe", "530", "30", "530", "1650", "350")
        time.sleep(.35)

    def seek(self, phrase, tries=5):
        for direction in ("up", "down"):
            for _ in range(tries):
                root = self.dump()
                for node in root.iter("node"):
                    if phrase.casefold() in self.label(node).casefold():
                        return node
                self.swipe(direction)
        (self.out / "last-ui.xml").write_bytes(ET.tostring(self.dump()))
        raise AssertionError(f"Could not find {phrase!r} after scrolling")

    def screenshot(self, name):
        data = self.adb_call("exec-out", "screencap", "-p", binary=True)
        self.require(data.startswith(b"\x89PNG\r\n\x1a\n"), f"PNG capture {name}")
        (self.out / name).write_bytes(data)
        self.mark("screenshot", name)

    @staticmethod
    def finalized_mp4(path):
        if not path.is_file() or path.stat().st_size < 100_000:
            return False
        data = path.read_bytes()
        offset = 0
        atoms = set()
        while offset + 8 <= len(data):
            size = int.from_bytes(data[offset:offset + 4], "big")
            kind = data[offset + 4:offset + 8]
            if size == 1 and offset + 16 <= len(data):
                size = int.from_bytes(data[offset + 8:offset + 16], "big")
            elif size == 0:
                size = len(data) - offset
            if size < 8 or offset + size > len(data):
                return False
            atoms.add(kind)
            offset += size
        return offset == len(data) and {b"ftyp", b"mdat", b"moov"} <= atoms

    def app(self):
        self.shell("am", "start", "-W", "-n", PRODUCT + "/.MainActivity")
        self.find("Make the next tap intentional.", timeout=20)

    def restricted_workaround(self):
        self.mark("restricted_settings_workaround", "Android sideload restriction; native App info menu")
        self.shell("am", "start", "-W", "-n", PRODUCT + "/.MainActivity")
        self.find("Off for now")
        self.tap("Open app info")
        self.find("App info")
        # Android Settings overflow has an icon description, then a labeled menu item.
        self.tap("More options")
        self.tap("Allow restricted settings")
        if self.visible("Confirm") or self.visible("Verify"):
            raise AssertionError("Restricted settings requires device authentication; fresh CI emulator must be unlocked")
        self.shell("am", "start", "-W", "-n", PRODUCT + "/.MainActivity")
        self.find("Off for now")
        self.tap("Turn on in Accessibility")

    def enable_accessibility(self):
        self.tap("Turn on in Accessibility")
        for phrase in ("Installed apps", "Downloaded apps"):
            if self.visible(phrase):
                self.tap(phrase)
                break
        self.tap("Speedbreaker")
        if self.visible("Restricted setting"):
            self.restricted_workaround()
            for phrase in ("Installed apps", "Downloaded apps"):
                if self.visible(phrase):
                    self.tap(phrase)
                    break
            self.tap("Speedbreaker")
        self.screenshot("accessibility-settings.png")
        switch = self.seek("Use Speedbreaker")
        self.tap_node(switch)
        self.mark("tap", "Use Speedbreaker native switch")
        if self.visible("Restricted setting"):
            self.restricted_workaround()
            self.tap("Speedbreaker")
            self.tap_node(self.seek("Use Speedbreaker"))
            self.mark("tap", "Use Speedbreaker after restricted-settings workaround")
        self.tap("Allow", exact=True)
        enabled = ""
        for _ in range(20):
            enabled = self.shell("settings", "get", "secure", "enabled_accessibility_services")
            if PRODUCT in enabled:
                break
            time.sleep(.5)
        self.require(PRODUCT in enabled, "native Accessibility service enabled")
        self.mark("native_accessibility_enabled", enabled)
        self.screenshot("accessibility-enabled.png")
        self.shell("input", "keyevent", "KEYCODE_BACK")
        self.shell("input", "keyevent", "KEYCODE_BACK")
        self.shell("am", "start", "-W", "-n", PRODUCT + "/.MainActivity")

    def grant_phone(self):
        self.tap_node(self.seek("Allow call-state access"))
        self.mark("tap", "Allow call-state access")
        self.tap("Allow", timeout=10, exact=True)
        package = ""
        for _ in range(10):
            package = self.shell("dumpsys", "package", PRODUCT)
            if "android.permission.READ_PHONE_STATE: granted=true" in package:
                break
            time.sleep(.5)
        self.require("android.permission.READ_PHONE_STATE: granted=true" in package,
                     "native Phone permission granted")
        self.mark("native_phone_granted")

    def select_target(self):
        self.tap_node(self.seek("Choose apps"))
        self.find("Search apps")
        self.tap("Search apps")
        self.shell("input", "text", "Speedbreaker%stest%starget")
        # The query text itself is also an Accessibility node with this label.
        target = next((node for node in self.dump().iter("node")
                       if self.label(node) == "Speedbreaker test target"
                       and "EditText" not in node.get("class", "")), None)
        self.require(target is not None, "synthetic target row visible apart from search field")
        self.tap_node(target)
        self.mark("selected_synthetic_target", FIXTURE)
        self.screenshot("chooser.png")
        self.shell("input", "keyevent", "KEYCODE_BACK")  # hide keyboard
        self.tap("Back")
        self.find("On for 1 app")
        self.screenshot("home.png")

    def verify_defaults(self):
        self.tap_node(self.seek("Defaults"))
        self.find("12 seconds")
        self.find("Always on")
        self.require(self.visible("12 seconds"), "12 second default breath")
        self.require(self.visible("Always on"), "all-hours default displayed")
        self.screenshot("defaults.png")
        self.tap("Back")

    def exercise_overlay(self):
        launched = time.monotonic()
        self.shell("am", "start", "-W", "-n", FIXTURE + "/.TargetActivity")
        self.find("Breathe", timeout=15)
        self.mark("overlay_started")
        self.screenshot("breath-locked.png")
        self.find("What are you here for?", timeout=14)
        self.require(time.monotonic() - launched >= 7.5, "choices follow initial eight-second lock")
        self.screenshot("choices-after-eight-seconds.png")
        continue_button, _ = self.find("Continue", timeout=7, exact=True)
        self.require(continue_button.get("enabled") == "true",
                     "Continue enabled after 12 second breath")
        # Preflight the native shade on this exact AVD profile. The timed second
        # launch uses its observed gear bounds without expensive XML dumps.
        self.tap("Leave")
        self.swipe("down")
        self.swipe("down")
        shade = self.dump()
        gear = next((node for node in shade.iter("node")
                     if self.label(node) in ("Settings", "Open settings")
                     and node.get("bounds")), None)
        if gear is None:
            (self.out / "preflight-shade.xml").write_bytes(ET.tostring(shade))
            raise AssertionError("Native SystemUI Settings gear not found in preflight shade")
        gear_bounds = gear.get("bounds")
        self.mark("systemui_gear_preflight", {"label": self.label(gear),
                                               "bounds": gear_bounds,
                                               "ui_sha256": hashlib.sha256(ET.tostring(shade)).hexdigest()})
        self.screenshot("systemui-preflight.png")
        self.shell("input", "keyevent", "KEYCODE_HOME")
        self.require(not self.visible("Quick Settings"), "preflight shade closed")
        # Start a second protected launch to test the SystemUI escape within hard lock.
        second_launch = time.monotonic()
        self.shell("am", "start", "-W", "-n", FIXTURE + "/.TargetActivity")
        self.find("Breathe", timeout=12)
        self.swipe("down")
        self.swipe("down")
        self.screenshot("systemui-during-lock.png")
        # Tap the Settings gear seen on the same native shade in preflight.
        self.tap_node(gear)
        gear_elapsed = time.monotonic() - second_launch
        self.mark("tap_native_systemui_settings", {"bounds": gear_bounds,
                                                     "seconds_after_target_launch": round(gear_elapsed, 2)})
        self.require(gear_elapsed < 8,
                     "SystemUI Settings tapped within initial eight-second lock")
        self.find("Settings", timeout=12)
        foreground = self.shell("dumpsys", "activity", "activities")
        focused = [line for line in foreground.splitlines()
                   if "topResumedActivity=" in line or "mResumedActivity:" in line]
        self.require(any("com.android.settings" in line for line in focused),
                     "native Settings escaped hard lock")
        self.screenshot("settings-escape.png")
        self.mark("native_settings_escape")

    def run(self):
        self.started = time.monotonic()
        self.validate()
        self.source = {"workflow_commit": subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
            "app_commit": self.args.app_commit,
            "app_apk_sha256": hashlib.sha256(self.args.app_apk.read_bytes()).hexdigest(),
            "fixture_apk_sha256": hashlib.sha256(self.args.fixture_apk.read_bytes()).hexdigest(),
            "api": self.shell("getprop", "ro.build.version.sdk"),
            "fingerprint": self.shell("getprop", "ro.build.fingerprint")}
        try:
            for apk in (self.args.app_apk, self.args.fixture_apk):
                self.adb_call("install", "-r", "-t", str(apk.resolve()), timeout=120)
            for package in (PRODUCT, FIXTURE):
                self.shell("am", "force-stop", package)
                self.require(self.shell("pm", "clear", package) == "Success", "cleared " + package)
            self.shell("input", "keyevent", "KEYCODE_WAKEUP")
            self.shell("wm", "dismiss-keyguard")
            self.video = subprocess.Popen(self.adb + ["shell", "screenrecord", "--time-limit", "180",
                                                     "/sdcard/sb-store-demo.mp4"],
                                          stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            self.started = time.monotonic()
            self.mark("recording_started")
            self.app()
            self.screenshot("welcome.png")
            for phrase in ("What it sees", "The first eight seconds", "What it keeps",
                           "No screen text or typed input", "No analytics, no cloud"):
                self.seek(phrase)
                self.mark("disclosure_seen", phrase)
            self.screenshot("full-disclosure.png")
            self.tap_node(self.seek("Not now"))
            self.mark("tap", "Not now — close")
            self.require(not self.visible("Make the next tap intentional."), "Not now closed Welcome")
            self.mark("declined_without_consent")
            self.app()
            self.require(self.visible("Make the next tap intentional."), "Welcome reopened without consent")
            self.seek("What it keeps")
            self.screenshot("acceptance-disclosure.png")
            self.tap_node(self.seek("I understand"))
            self.mark("tap", "I understand — continue")
            self.find("Off for now", timeout=15)
            self.mark("accepted_after_disclosure")
            self.enable_accessibility()
            self.grant_phone()
            self.select_target()
            self.verify_defaults()
            self.exercise_overlay()
            self.require(time.monotonic() - self.started < 180,
                         "full demo completed within screen recording limit")
            self.mark("demo_complete")
        except Exception as error:
            self.mark("failure", str(error))
            try:
                self.screenshot("failure.png")
                (self.out / "failure-ui.xml").write_bytes(ET.tostring(self.dump()))
            except Exception as capture_error:
                self.mark("failure_capture_error", str(capture_error))
            raise
        finally:
            if self.video:
                # End the Android encoder itself so it writes the MP4 moov atom.
                for pid in self.shell("pidof", "screenrecord", check=False).split():
                    self.shell("kill", "-2", pid, check=False)
                try:
                    self.video.communicate(timeout=10)
                except subprocess.TimeoutExpired:
                    self.video.kill()
                    self.video.communicate()
                self.adb_call("pull", "/sdcard/sb-store-demo.mp4", str(self.out / "first-run.mp4"), check=False)
                self.shell("rm", "/sdcard/sb-store-demo.mp4", check=False)
            video_valid = self.finalized_mp4(self.out / "first-run.mp4")
            self.assertions["finalized screen recording"] = video_valid
            captures = {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                        for p in self.out.iterdir() if p.suffix in (".png", ".mp4") and p.stat().st_size}
            (self.out / "evidence.json").write_text(json.dumps({
                "source": self.source,
                "events": self.events, "assertions": self.assertions,
                "capture_sha256": captures,
            }, indent=2) + "\n")
            if not video_valid:
                raise AssertionError("Screen recording did not finalize; first-run.mp4 is not a valid MP4")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--reset-test-packages", action="store_true")
    parser.add_argument("--app-apk", type=Path, required=True)
    parser.add_argument("--fixture-apk", type=Path, required=True)
    parser.add_argument("--app-commit", required=True)
    parser.add_argument("--output", type=Path, default=ROOT / "build/store-demo")
    Demo(parser.parse_args()).run()


if __name__ == "__main__":
    main()
