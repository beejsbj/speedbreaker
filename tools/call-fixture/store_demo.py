#!/usr/bin/env python3
"""Record an actual first-run policy demo on an explicitly disposable Android emulator."""

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import uuid
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
        if any(self.out.iterdir()):
            raise AssertionError("Refusing stale store-demo output; use a fresh evidence directory")
        self.events = []
        self.assertions = {}
        self.video = None
        self.observer = None
        self.observer_sequence = 0
        self.recording = False
        self.remote_video = "/sdcard/sb-store-demo-" + uuid.uuid4().hex + ".mp4"
        self.display_width = 0
        self.display_height = 0
        self.listing_failures = []

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
                 "video_seconds": round(time.monotonic() - self.started, 2)
                 if self.recording else None, "event": name}
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

    def start_observer(self):
        # Instrumentation starts and finishes by force-stopping its target package.
        # Start it once before the fixture target is opened, and keep it alive.
        self.observer = subprocess.Popen(self.adb + ["shell", "am", "instrument", "-w",
                                                  FIXTURE + "/.UiHierarchy"],
                                         stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            if self.shell("run-as", FIXTURE, "ls", "files/ui-ready", check=False):
                self.mark("ui_observer_ready", "persistent nonsuppressing fixture instrumentation")
                return
            if self.observer.poll() is not None:
                output, error = self.observer.communicate()
                raise AssertionError(f"UI observer exited at start: {output!r} {error!r}")
            time.sleep(.1)
        raise AssertionError("UI observer did not become ready")

    def dump(self, all_windows=False):
        if self.observer is None or self.observer.poll() is not None:
            raise AssertionError("Persistent UI observer is not running")
        self.observer_sequence += 1
        suffix = ("all-" if all_windows else "") + str(self.observer_sequence)
        self.shell("run-as", FIXTURE, "touch", "files/ui-request-" + suffix)
        deadline = time.monotonic() + 12
        last_response = b""
        while time.monotonic() < deadline:
            read = subprocess.run(self.adb + ["shell", "-T", "run-as", FIXTURE, "cat",
                                              "files/ui-response-" + suffix + ".xml"],
                                  capture_output=True, timeout=30)
            raw = read.stdout
            if raw.lstrip().startswith(b"<?xml"):
                if read.returncode:
                    raise AssertionError(f"UI observer XML transport failed: exit={read.returncode} "
                                         f"stderr={read.stderr[-200:]!r}")
                try:
                    root = ET.fromstring(raw)
                except ET.ParseError as error:
                    raise AssertionError(f"UI observer response {suffix} invalid XML: {error}") from error
                if root.tag == "error":
                    raise AssertionError("UI observer response: " + root.get("message", "unknown error"))
                return root
            if raw:
                last_response = raw[:200]
            if self.observer.poll() is not None:
                raise AssertionError("Persistent UI observer exited during hierarchy query")
            time.sleep(.1)
        raise AssertionError(f"UI observer response {suffix} timed out; last={last_response!r}")

    def stop_observer(self):
        if self.observer is None:
            return
        if self.observer.poll() is None:
            self.shell("run-as", FIXTURE, "touch", "files/ui-stop", check=False)
        try:
            output, error = self.observer.communicate(timeout=5)
        except subprocess.TimeoutExpired:
            self.observer.kill()
            output, error = self.observer.communicate()
        report = output.decode(errors="replace") + error.decode(errors="replace")
        self.assertions["persistent UI observer finished cleanly"] = (
            "INSTRUMENTATION_RESULT: result=PASS" in report)
        self.mark("ui_observer_finished", report[-800:])

    @staticmethod
    def label(node):
        return " ".join((node.get("text", ""), node.get("content-desc", ""))).strip()

    def onscreen(self, node):
        bounds = list(map(int, re.findall(r"-?\d+", node.get("bounds", ""))))
        return (node.get("visible-to-user") == "true" and len(bounds) == 4
                and bounds[2] > bounds[0] and bounds[3] > bounds[1]
                and bounds[0] < self.display_width and bounds[2] > 0
                and bounds[1] < self.display_height and bounds[3] > 0)

    def find(self, phrase, timeout=12, exact=False, tappable=False):
        end = time.monotonic() + timeout
        while time.monotonic() < end:
            root = self.dump()
            for node in root.iter("node"):
                if not self.onscreen(node):
                    continue
                label = self.label(node)
                if (label == phrase if exact else phrase.casefold() in label.casefold()):
                    if tappable:
                        coords = list(map(int, re.findall(r"\d+", node.get("bounds", ""))))
                        if (len(coords) != 4 or coords[2] <= coords[0]
                                or coords[3] <= coords[1] or node.get("enabled") != "true"):
                            continue
                    return node, root
            time.sleep(.5)
        raise AssertionError(f"UI did not show {phrase!r}; see last-ui.xml")

    def tap_node(self, node):
        self.require(self.onscreen(node), "tapped node visible on actual display")
        bounds = node.get("bounds", "")
        coords = list(map(int, re.findall(r"\d+", bounds)))
        if len(coords) != 4 or coords[2] <= coords[0] or coords[3] <= coords[1]:
            raise AssertionError(f"No tappable bounds: {ET.tostring(node, encoding='unicode')}")
        self.shell("input", "tap", str((coords[0] + coords[2]) // 2),
                   str((coords[1] + coords[3]) // 2))

    def tap(self, phrase, **kw):
        node, _ = self.find(phrase, tappable=True, **kw)
        self.tap_node(node)
        self.mark("tap", phrase)

    def accept_disclosure(self):
        for attempt in range(2):
            candidate = self.seek("I understand")
            time.sleep(.35)
            hierarchy = self.dump()
            stable = next((node for node in hierarchy.iter("node")
                           if self.label(node) == self.label(candidate)
                           and node.get("bounds") == candidate.get("bounds")
                           and self.onscreen(node) and node.get("enabled") == "true"), None)
            if stable is None:
                continue
            self.screenshot("accept-button-before-tap.png")
            self.mark("accept_button_observed", {"attempt": attempt + 1,
                                                  "bounds": stable.get("bounds"),
                                                  "label": self.label(stable)})
            self.tap_node(stable)
            self.mark("tap", "I understand — continue")
            try:
                self.find("Off for now", timeout=4)
                self.mark("accepted_after_disclosure")
                return
            except AssertionError:
                if not self.visible("Make the next tap intentional."):
                    raise AssertionError("Acceptance did not show Home and Welcome is absent")
                self.mark("acceptance_still_on_welcome", {"attempt": attempt + 1})
        raise AssertionError("Visible acceptance tap did not open Home")

    def decline_disclosure(self):
        for attempt in range(2):
            candidate = self.seek("Not now")
            time.sleep(.35)
            hierarchy = self.dump()
            stable = next((node for node in hierarchy.iter("node")
                           if self.label(node) == self.label(candidate)
                           and node.get("bounds") == candidate.get("bounds")
                           and self.onscreen(node) and node.get("enabled") == "true"), None)
            if stable is None:
                continue
            self.mark("decline_button_observed", {"attempt": attempt + 1,
                                                   "bounds": stable.get("bounds")})
            self.tap_node(stable)
            self.mark("tap", "Not now — close")
            deadline = time.monotonic() + 4
            while time.monotonic() < deadline:
                if not self.visible("Make the next tap intentional."):
                    self.mark("declined_without_consent")
                    return
                time.sleep(.25)
            self.mark("decline_still_on_welcome", {"attempt": attempt + 1})
        raise AssertionError("Visible Not now tap did not close Welcome")

    def visible(self, phrase):
        return any(self.onscreen(n) and phrase.casefold() in self.label(n).casefold()
                   for n in self.dump().iter("node"))

    def swipe(self, direction="up"):
        if direction == "up":
            self.shell("input", "swipe", "530", "1750", "530", "500", "450")
        elif direction == "down":
            self.shell("input", "swipe", "530", "500", "530", "1750", "450")
        elif direction == "shade":
            self.shell("input", "swipe", "530", "30", "530", "1650", "350")
        elif direction == "expand_shade":
            self.shell("input", "swipe", "530", "380", "530", "1650", "350")
        else:
            raise ValueError(direction)
        time.sleep(.35)

    def seek(self, phrase, tries=5):
        for direction in ("up", "down"):
            for _ in range(tries):
                root = self.dump()
                for node in root.iter("node"):
                    if self.onscreen(node) and phrase.casefold() in self.label(node).casefold():
                        return node
                self.swipe(direction)
        (self.out / "last-ui.xml").write_bytes(ET.tostring(self.dump()))
        raise AssertionError(f"Could not find {phrase!r} after scrolling")

    def screenshot(self, name):
        data = self.adb_call("exec-out", "screencap", "-p", binary=True)
        self.require(data.startswith(b"\x89PNG\r\n\x1a\n"), f"PNG capture {name}")
        (self.out / name).write_bytes(data)
        self.mark("screenshot", name)

    def listing_jpeg(self, name):
        if self.args.policy_diagnostic_tall:
            self.mark("listing_capture_skipped", {"file": name,
                                                   "reason": "manual tall policy diagnostic"})
            return
        data = b""
        if int(self.source["api"]) < 36:
            self.observer_sequence += 1
            suffix = str(self.observer_sequence)
            self.shell("run-as", FIXTURE, "touch", "files/ui-jpeg-" + suffix)
            deadline = time.monotonic() + 12
            while time.monotonic() < deadline:
                read = subprocess.run(self.adb + ["shell", "-T", "run-as", FIXTURE, "cat",
                                                  "files/ui-image-" + suffix + ".jpg"],
                                      capture_output=True, timeout=30)
                data = read.stdout
                if data.startswith(b"\xff\xd8\xff") and data.endswith(b"\xff\xd9"):
                    if read.returncode:
                        self.listing_failures.append(name + ": JPEG transport failed")
                        self.mark("native_jpeg_transport_failure", {"file": name,
                                                                     "exit": read.returncode})
                        return
                    break
                if self.observer.poll() is not None:
                    break
                time.sleep(.1)
            method = "UiAutomation.takeScreenshot + Android Bitmap JPEG"
        else:
            for attempt in range(3):
                capture = subprocess.run(self.adb + ["shell", "-T", "screencap", "-j"],
                                         capture_output=True, timeout=30)
                data = capture.stdout
                if capture.returncode:
                    self.listing_failures.append(name + ": shell screenshot command failed")
                    self.mark("native_jpeg_transport_failure", {"file": name,
                                                                 "exit": capture.returncode,
                                                                 "stderr": capture.stderr[-500:].decode(errors="replace"),
                                                                 "head": data[:24].hex(),
                                                                 "tail": data[-24:].hex()})
                    return
                if data.startswith(b"\xff\xd8\xff") and data.endswith(b"\xff\xd9"):
                    break
                self.mark("native_jpeg_retry", {"file": name, "attempt": attempt + 1,
                                                 "bytes": len(data), "head": data[:24].hex(),
                                                 "tail": data[-24:].hex()})
                time.sleep(.3)
            method = "Android shell -T screencap -j"
        if not (data.startswith(b"\xff\xd8\xff") and data.endswith(b"\xff\xd9")):
            self.mark("native_jpeg_failure", {"file": name, "method": method,
                                               "bytes": len(data), "head": data[:24].hex(),
                                               "tail": data[-24:].hex()})
            self.listing_failures.append(name + ": native JPEG unavailable through " + method)
            return
        dimensions = None
        offset = 2
        while offset + 4 < len(data):
            if data[offset] != 0xff:
                break
            marker = data[offset + 1]
            if marker in (0xc0, 0xc1, 0xc2, 0xc3) and offset + 9 <= len(data):
                dimensions = (int.from_bytes(data[offset + 7:offset + 9], "big"),
                              int.from_bytes(data[offset + 5:offset + 7], "big"))
                break
            if marker in (0xd8, 0xd9) or 0xd0 <= marker <= 0xd7:
                offset += 2
            else:
                offset += 2 + int.from_bytes(data[offset + 2:offset + 4], "big")
        if dimensions != (1080, 1920):
            self.listing_failures.append(f"{name}: native dimensions {dimensions}")
            self.mark("native_jpeg_wrong_dimensions", {"file": name, "dimensions": dimensions})
            return
        destination = self.out / "listing" / name
        destination.parent.mkdir(exist_ok=True)
        destination.write_bytes(data)
        self.mark("native_listing_capture", {"file": str(destination.relative_to(self.out)),
                                              "method": method,
                                              "sha256": hashlib.sha256(data).hexdigest()})

    def overlay_visible(self):
        report = self.shell("dumpsys", "accessibility")
        return any("TYPE_ACCESSIBILITY_OVERLAY" in line and "Speedbreaker" in line
                   for line in report.splitlines())

    def wait_overlay(self, expected, timeout):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            if self.overlay_visible() == expected:
                self.mark("overlay_window", {"visible": expected})
                return
            time.sleep(.2)
        raise AssertionError(f"Accessibility overlay did not become visible={expected}")

    def wait_service_bound(self, timeout=8):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            report = self.shell("dumpsys", "accessibility")
            if "Bound services:{Service[label=Speedbreaker attention pauses" in report:
                self.mark("accessibility_service_bound")
                return
            time.sleep(.2)
        raise AssertionError("Speedbreaker Accessibility service did not bind")

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

    def open_accessibility_detail(self):
        for attempt in range(3):
            if self.visible("Use Speedbreaker"):
                return
            self.seek("Speedbreaker attention pauses")
            time.sleep(.35)
            root = self.dump()
            if any("Use Speedbreaker" in self.label(node) and self.onscreen(node)
                   for node in root.iter("node")):
                self.mark("native_accessibility_detail_open")
                return
            row = next((node for node in root.iter("node")
                        if self.label(node) == "Speedbreaker attention pauses"
                        and self.onscreen(node)), None)
            if row is None:
                continue
            self.require(row.get("package") == "com.android.settings",
                         "native Accessibility service row belongs to Settings")
            parents = {child: parent for parent in root.iter() for child in parent}
            target = row
            while target in parents and target.get("clickable") != "true":
                target = parents[target]
            self.require(target.get("clickable") == "true",
                         "native Accessibility service row has clickable ancestor")
            self.tap_node(target)
            self.mark("tap_native_accessibility_row", {"attempt": attempt + 1,
                                                        "bounds": target.get("bounds")})
            try:
                self.find("Use Speedbreaker", timeout=8)
                return
            except AssertionError:
                pass
        raise AssertionError("Native Speedbreaker Accessibility detail did not open")

    def enable_accessibility(self):
        self.tap("Turn on in Accessibility")
        for phrase in ("Installed apps", "Downloaded apps"):
            if self.visible(phrase):
                self.tap(phrase)
                break
        self.open_accessibility_detail()
        if self.visible("Restricted setting"):
            self.restricted_workaround()
            for phrase in ("Installed apps", "Downloaded apps"):
                if self.visible(phrase):
                    self.tap(phrase)
                    break
            self.open_accessibility_detail()
        self.screenshot("accessibility-settings.png")
        switch = self.seek("Use Speedbreaker")
        self.tap_node(switch)
        self.mark("tap", "Use Speedbreaker native switch")
        if self.visible("Restricted setting"):
            self.restricted_workaround()
            self.open_accessibility_detail()
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
        # Settings is stacked above MainActivity in the app's task. Starting the
        # task again merely brings Settings back; navigate each page in the UI.
        for step in range(4):
            root = self.dump()
            app_visible = any(node.get("package") == PRODUCT and self.onscreen(node)
                              for node in root.iter("node"))
            welcome_visible = any("Make the next tap intentional." in self.label(node)
                                  and self.onscreen(node) for node in root.iter("node"))
            if app_visible and not welcome_visible:
                activity = self.shell("dumpsys", "activity", "activities")
                focused = [line for line in activity.splitlines()
                           if "topResumedActivity=" in line or "mResumedActivity:" in line]
                self.require(any(PRODUCT + "/.MainActivity" in line for line in focused),
                             "Speedbreaker Home foreground after native grant")
                rationale = self.seek("Allow call-state access")
                self.require(rationale.get("package") == PRODUCT and self.onscreen(rationale),
                             "Phone rationale visibly available on Speedbreaker Home")
                self.mark("returned_home_from_native_accessibility", {"back_steps": step})
                break
            self.require(any(node.get("package") == "com.android.settings"
                             for node in root.iter("node")),
                         "native Settings visible before Back to Speedbreaker")
            before = tuple((node.get("package"), self.label(node)) for node in root.iter("node")
                           if self.onscreen(node) and self.label(node)
                           and node.get("package") in (PRODUCT, "com.android.settings"))
            self.shell("input", "keyevent", "KEYCODE_BACK")
            deadline = time.monotonic() + 5
            while time.monotonic() < deadline:
                after = self.dump()
                current = tuple((node.get("package"), self.label(node)) for node in after.iter("node")
                                if self.onscreen(node) and self.label(node)
                                and node.get("package") in (PRODUCT, "com.android.settings"))
                if current and current != before:
                    self.mark("native_settings_back_transition", {"step": step + 1})
                    break
                time.sleep(.2)
            else:
                raise AssertionError("Native Settings did not change after Back")
        else:
            raise AssertionError("Native Settings Back never returned to Speedbreaker Home")

    def grant_phone(self):
        self.wait_service_bound()
        activity = self.shell("dumpsys", "activity", "activities")
        focused = [line for line in activity.splitlines()
                   if "topResumedActivity=" in line or "mResumedActivity:" in line]
        self.require(any(PRODUCT + "/.MainActivity" in line for line in focused),
                     "Speedbreaker Home foreground before Phone rationale")
        button = self.seek("Allow call-state access")
        self.require(button.get("package") == PRODUCT,
                     "Phone rationale button belongs to Speedbreaker")
        self.wait_service_bound()
        self.screenshot("phone-rationale.png")
        self.tap_node(button)
        self.mark("tap", "Allow call-state access")
        package = ""
        for attempt in range(2):
            deadline = time.monotonic() + 10
            previous = None
            button = None
            while time.monotonic() < deadline:
                hierarchy = self.dump()
                title = any("Allow Speedbreaker to make and manage phone calls?" in self.label(n)
                            for n in hierarchy.iter("node"))
                current = next((n for n in hierarchy.iter("node")
                                if n.get("package") == "com.google.android.permissioncontroller"
                                and n.get("resource-id", "").endswith("permission_allow_button")
                                and self.label(n) == "Allow" and n.get("enabled") == "true"), None)
                bounds = current.get("bounds") if current is not None and title else None
                if bounds and bounds == previous:
                    button = current
                    break
                previous = bounds
                time.sleep(.3)
            self.require(button is not None, "native Phone dialog Allow bounds stable across two reads")
            self.mark("native_phone_allow_observed", {"attempt": attempt + 1,
                                                        "bounds": button.get("bounds")})
            self.tap_node(button)
            self.mark("tap_native_phone_allow", {"attempt": attempt + 1,
                                                  "bounds": button.get("bounds")})
            for _ in range(8):
                package = self.shell("dumpsys", "package", PRODUCT)
                if "android.permission.READ_PHONE_STATE: granted=true" in package:
                    break
                time.sleep(.3)
            if "android.permission.READ_PHONE_STATE: granted=true" in package:
                break
            if not any("Allow Speedbreaker to make and manage phone calls?" in self.label(n)
                       for n in self.dump().iter("node")):
                break
        self.require("android.permission.READ_PHONE_STATE: granted=true" in package,
                     "native Phone permission granted")
        self.require(not any("Allow Speedbreaker to make and manage phone calls?" in self.label(n)
                             for n in self.dump().iter("node")), "native Phone dialog closed")
        self.mark("native_phone_granted")

    def select_target(self):
        self.tap_node(self.seek("Choose apps"))
        self.find("Search apps")
        self.tap("Search apps")
        # A short unique query avoids dropped/interleaved key events on the cold
        # API 36 keyboard (observed with the full synthetic target name).
        self.shell("input", "text", "target")
        self.require(any(node.get("text") == "target" and "EditText" in node.get("class", "")
                         for node in self.dump().iter("node")),
                     "synthetic target search query entered exactly")
        # The query text itself is also an Accessibility node with this label.
        deadline = time.monotonic() + 8
        target = None
        while time.monotonic() < deadline and target is None:
            target = next((node for node in self.dump().iter("node")
                           if self.label(node) == "Speedbreaker test target"
                           and "EditText" not in node.get("class", "")), None)
            if target is None:
                time.sleep(.3)
        self.require(target is not None, "synthetic target row visible apart from search field")
        self.tap_node(target)
        self.mark("selected_synthetic_target", FIXTURE)
        self.screenshot("chooser.png")
        self.shell("input", "keyevent", "KEYCODE_BACK")  # hide keyboard
        self.tap("Back")
        self.find("Speedbreaker test target")
        self.screenshot("home.png")
        self.listing_jpeg("02-home.jpg")

    def verify_defaults(self):
        self.tap_node(self.seek("Defaults"))
        self.find("12 seconds")
        self.require(self.visible("12 seconds"), "12 second default breath")
        self.seek("Always on")
        self.require(self.visible("Always on"), "all-hours default displayed")
        self.screenshot("defaults.png")
        self.listing_jpeg("03-defaults.jpg")
        self.shell("input", "keyevent", "KEYCODE_BACK")
        self.find("Speedbreaker test target")
        self.mark("defaults_returned_home_via_back")

    def exercise_overlay(self):
        self.wait_service_bound()
        launched = time.monotonic()
        self.shell("am", "start", "-W", "-n", FIXTURE + "/.TargetActivity")
        self.wait_overlay(True, timeout=6)
        overlay_seen = time.monotonic()
        target_pid = self.shell("pidof", FIXTURE)
        self.require(bool(target_pid), "synthetic target process alive under overlay")
        self.mark("overlay_started")
        self.screenshot("breath-locked.png")
        self.require(time.monotonic() - launched < 8,
                     "locked breath captured inside initial eight seconds")
        time.sleep(max(0, 9.0 - (time.monotonic() - overlay_seen)))
        self.wait_overlay(True, timeout=2)
        self.screenshot("choices-after-eight-seconds.png")
        self.require(time.monotonic() - overlay_seen >= 8,
                     "choices capture follows eight seconds of visible overlay")
        time.sleep(max(0, 14.0 - (time.monotonic() - overlay_seen)))
        self.wait_overlay(True, timeout=2)
        deadline = time.monotonic() + 5
        continue_enabled = False
        while time.monotonic() < deadline:
            hierarchy = self.dump()
            continue_enabled = any(self.label(node) == "Continue" and node.get("enabled") == "true"
                                   for node in hierarchy.iter("node"))
            if continue_enabled:
                break
            time.sleep(.3)
        self.require(continue_enabled, "Continue enabled after full breath")
        self.require(self.shell("pidof", FIXTURE) == target_pid,
                     "UI observer kept synthetic target process alive")
        self.wait_overlay(True, timeout=2)
        self.screenshot("choices-ready.png")
        self.listing_jpeg("04-choices.jpg")
        self.require(time.monotonic() - overlay_seen >= 12,
                     "ready capture follows full 12-second visible breath")
        # Calibrate the actual SystemUI gesture from Launcher. This establishes
        # the AVD's native gear, but makes no claim about access during lock.
        self.shell("input", "keyevent", "KEYCODE_HOME")
        self.wait_overlay(False, timeout=3)
        self.wait_service_bound()
        gestures = []
        gear = None
        for gesture in ("shade", "expand_shade"):
            gestures.append(gesture)
            self.swipe(gesture)
            shade = self.dump()
            step = len(gestures)
            (self.out / f"preflight-shade-{step}.xml").write_bytes(ET.tostring(shade))
            (self.out / f"preflight-windows-{step}.xml").write_bytes(
                ET.tostring(self.dump(all_windows=True)))
            (self.out / f"preflight-window-{step}.txt").write_text(
                self.shell("dumpsys", "window", "windows"))
            gear = next((node for node in shade.iter("node")
                         if node.get("package") == "com.android.systemui"
                         and self.label(node).rstrip(".").casefold() in ("settings", "open settings")
                         and node.get("bounds")), None)
            self.mark("systemui_preflight_observed", {"gesture": gesture,
                                                      "gear_visible": gear is not None,
                                                      "active_packages": sorted({n.get("package", "")
                                                                                 for n in shade.iter("node")})})
            if gear is not None:
                break
            if not any(node.get("package") == "com.android.systemui"
                       for node in shade.iter("node")):
                break
        if gear is None:
            (self.out / "preflight-shade.xml").write_bytes(ET.tostring(shade))
            (self.out / "preflight-windows.xml").write_bytes(ET.tostring(self.dump(all_windows=True)))
            (self.out / "preflight-window.txt").write_text(
                self.shell("dumpsys", "window", "windows"))
            raise AssertionError("Native SystemUI Settings gear not found in preflight shade")
        gear_bounds = gear.get("bounds")
        self.mark("systemui_gear_preflight", {"label": self.label(gear),
                                               "bounds": gear_bounds,
                                               "gestures": gestures,
                                               "ui_sha256": hashlib.sha256(ET.tostring(shade)).hexdigest()})
        self.screenshot("systemui-preflight.png")
        self.shell("input", "keyevent", "KEYCODE_HOME")
        self.wait_overlay(False, timeout=3)
        self.wait_service_bound()
        # Start a second protected launch to measure actual SystemUI access in
        # its own initial hard-lock interval.
        second_launch = time.monotonic()
        self.shell("am", "start", "-W", "-n", FIXTURE + "/.TargetActivity")
        self.wait_overlay(True, timeout=5)
        timed_gear = None
        for gesture in gestures:
            self.swipe(gesture)
            timed_shade = self.dump()
            timed_gear = next((node for node in timed_shade.iter("node")
                               if node.get("package") == "com.android.systemui"
                               and self.label(node).rstrip(".").casefold() in ("settings", "open settings")
                               and node.get("bounds")), None)
            elapsed = time.monotonic() - second_launch
            self.mark("initial_lock_shade_observed", {"gesture": gesture,
                                                      "seconds_after_target_launch": round(elapsed, 2),
                                                      "gear_visible": timed_gear is not None,
                                                      "active_packages": sorted({n.get("package", "")
                                                                                 for n in timed_shade.iter("node")})})
            if timed_gear is not None:
                break
            if self.args.policy_diagnostic_tall:
                step = gestures.index(gesture) + 1
                (self.out / f"locked-shade-{step}.xml").write_bytes(ET.tostring(timed_shade))
                (self.out / f"locked-windows-{step}.xml").write_bytes(
                    ET.tostring(self.dump(all_windows=True)))
                self.screenshot(f"locked-shade-{step}.png")
                (self.out / f"locked-window-{step}.txt").write_text(
                    self.shell("dumpsys", "window", "windows"))
                self.mark("initial_lock_window_snapshot", {"step": step,
                                                             "seconds_after_target_launch":
                                                             round(time.monotonic() - second_launch, 2)})
            if not any(node.get("package") == "com.android.systemui"
                       for node in timed_shade.iter("node")):
                break
        if timed_gear is None:
            self.mark("timed_gear_absent", {"seconds_after_target_launch":
                                             round(time.monotonic() - second_launch, 2)})
            (self.out / "timed-shade.xml").write_bytes(ET.tostring(timed_shade))
            (self.out / "timed-windows.xml").write_bytes(ET.tostring(self.dump(all_windows=True)))
            (self.out / "timed-window.txt").write_text(
                self.shell("dumpsys", "window", "windows"))
            raise AssertionError("Native SystemUI gear absent during initial lock after preflight gestures")
        self.screenshot("systemui-during-lock.png")
        # Tap the actual native gear visible on this protected launch.
        self.tap_node(timed_gear)
        gear_elapsed = time.monotonic() - second_launch
        self.mark("tap_native_systemui_settings", {"bounds": timed_gear.get("bounds"),
                                                     "preflight_bounds": gear_bounds,
                                                     "seconds_after_target_launch": round(gear_elapsed, 2)})
        self.require(gear_elapsed < 8,
                     "SystemUI Settings tapped within initial eight-second lock")
        settings_observed = False
        while time.monotonic() - second_launch < 8:
            foreground = self.shell("dumpsys", "activity", "activities")
            focused = [line for line in foreground.splitlines()
                       if "topResumedActivity=" in line or "mResumedActivity:" in line]
            if any("com.android.settings" in line for line in focused):
                settings_observed = True
                self.mark("native_settings_resumed", {"seconds_after_target_launch":
                                                      round(time.monotonic() - second_launch, 2),
                                                      "focused": focused})
                break
            time.sleep(.1)
        self.require(settings_observed and time.monotonic() - second_launch < 8,
                     "native Settings resumed within initial eight-second lock")
        self.find("Settings", timeout=8)
        self.screenshot("settings-escape.png")
        self.mark("native_settings_escape")
        self.tap_node(self.seek("Accessibility"))
        for phrase in ("Installed apps", "Downloaded apps"):
            if self.visible(phrase):
                self.tap(phrase)
                break
        self.open_accessibility_detail()
        self.tap_node(self.seek("Use Speedbreaker"))
        if self.visible("Stop"):
            self.tap("Stop", exact=True)
        enabled = ""
        for _ in range(20):
            enabled = self.shell("settings", "get", "secure", "enabled_accessibility_services")
            if PRODUCT not in enabled:
                break
            time.sleep(.5)
        self.require(PRODUCT not in enabled, "native Accessibility service disabled at end")
        self.screenshot("accessibility-disabled.png")
        self.mark("native_accessibility_disabled")

    def run(self):
        self.started = time.monotonic()
        self.validate()
        self.source = {"workflow_commit": subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
            "app_commit": self.args.app_commit,
            "app_apk_sha256": hashlib.sha256(self.args.app_apk.read_bytes()).hexdigest(),
            "fixture_apk_sha256": hashlib.sha256(self.args.fixture_apk.read_bytes()).hexdigest(),
            "api": self.shell("getprop", "ro.build.version.sdk"),
            "fingerprint": self.shell("getprop", "ro.build.fingerprint"),
            "capture_mode": "tall_policy_diagnostic" if self.args.policy_diagnostic_tall
            else "listing_and_policy"}
        try:
            meminfo = self.shell("cat", "/proc/meminfo")
            self.source["emulator_memtotal"] = meminfo.splitlines()[0]
            self.source["emulator_cpu_online"] = self.shell("cat", "/sys/devices/system/cpu/online")
            (self.out / "avd-meminfo.txt").write_text(meminfo + "\n")
            avd_root = Path(os.environ.get("ANDROID_AVD_HOME", str(Path.home() / ".android/avd")))
            configs = sorted(avd_root.glob("*.avd/config.ini"))
            self.source["avd_config_sha256"] = {}
            for config in configs:
                data = config.read_bytes()
                (self.out / ("avd-config-" + config.parent.name + ".ini")).write_bytes(data)
                self.source["avd_config_sha256"][config.parent.name] = hashlib.sha256(data).hexdigest()
            self.mark("emulator_resources", {"memtotal": self.source["emulator_memtotal"],
                                              "cpu_online": self.source["emulator_cpu_online"],
                                              "avd_configs": list(self.source["avd_config_sha256"])})
            # The preceding call regression grants Phone permission. pm clear does
            # not revoke runtime grants, so reinstall both test packages afresh.
            for package in (PRODUCT, FIXTURE):
                installed = self.shell("pm", "path", package, check=False).startswith("package:")
                if installed:
                    result = self.adb_call("uninstall", package, check=False)
                    self.require(result == "Success", "uninstalled test package " + package)
                    self.require(not self.shell("pm", "path", package, check=False).startswith("package:"),
                                 "package absent after uninstall " + package)
                else:
                    self.mark("fresh_package_absent", package)
            for apk in (self.args.app_apk, self.args.fixture_apk):
                self.adb_call("install", "-t", str(apk.resolve()), timeout=120)
            for package in (PRODUCT, FIXTURE):
                self.shell("am", "force-stop", package)
                self.require(self.shell("pm", "clear", package) == "Success", "cleared " + package)
            permissions = self.shell("dumpsys", "package", PRODUCT)
            self.require("android.permission.READ_PHONE_STATE: granted=true" not in permissions,
                         "fresh install has no Phone grant before UI consent")
            self.shell("input", "keyevent", "KEYCODE_WAKEUP")
            self.shell("wm", "dismiss-keyguard")
            if not self.args.policy_diagnostic_tall:
                self.shell("wm", "size", "1080x1920")
            size_report = self.shell("wm", "size")
            size_line = re.search(r"Override size: (\d+)x(\d+)", size_report)
            if size_line is None:
                size_line = re.search(r"Physical size: (\d+)x(\d+)", size_report)
            self.require(size_line is not None, "emulator display dimensions available")
            self.display_width, self.display_height = map(int, size_line.groups())
            expected = (1080, 2400) if self.args.policy_diagnostic_tall else (1080, 1920)
            self.require((self.display_width, self.display_height) == expected,
                         "disposable emulator expected actual display dimensions")
            self.mark("display_size", {"width": self.display_width, "height": self.display_height,
                                       "mode": "tall_policy_diagnostic" if self.args.policy_diagnostic_tall
                                       else "listing_and_policy"})
            self.adb_call("logcat", "-c")
            self.start_observer()
            self.video = subprocess.Popen(self.adb + ["shell", "screenrecord", "--time-limit", "180",
                                                     self.remote_video],
                                          stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            self.started = time.monotonic()
            self.recording = True
            self.mark("recording_started")
            self.app()
            self.screenshot("welcome.png")
            self.listing_jpeg("01-welcome.jpg")
            for phrase in ("What it sees", "The first eight seconds", "What it keeps",
                           "No screen text or typed input", "No analytics, no cloud"):
                self.seek(phrase)
                self.mark("disclosure_seen", phrase)
            self.seek("I understand")
            self.mark("disclosure_footer_visible")
            self.screenshot("full-disclosure.png")
            self.decline_disclosure()
            self.app()
            self.require(self.visible("Make the next tap intentional."), "Welcome reopened without consent")
            self.seek("What it keeps")
            self.screenshot("acceptance-disclosure.png")
            self.accept_disclosure()
            self.enable_accessibility()
            self.grant_phone()
            self.select_target()
            self.verify_defaults()
            self.exercise_overlay()
            if self.args.policy_diagnostic_tall:
                self.assertions["listing assets deliberately skipped in tall diagnostic"] = True
            else:
                self.require(not self.listing_failures and len(list((self.out / "listing").glob("*.jpg"))) == 4,
                             "four native 1080x1920 JPEG listing captures")
            self.require(time.monotonic() - self.started < 180,
                         "full demo completed within screen recording limit")
            self.mark("policy_diagnostic_complete" if self.args.policy_diagnostic_tall else "demo_complete")
        except Exception as error:
            self.mark("failure", str(error))
            try:
                self.screenshot("failure.png")
                (self.out / "failure-ui.xml").write_bytes(ET.tostring(self.dump()))
                (self.out / "failure-windows.xml").write_bytes(
                    ET.tostring(self.dump(all_windows=True)))
            except Exception as capture_error:
                self.mark("failure_capture_error", str(capture_error))
            for name, command in (
                ("failure-accessibility.txt", ("dumpsys", "accessibility")),
                ("failure-window.txt", ("dumpsys", "window", "windows")),
                ("failure-statusbar.txt", ("dumpsys", "statusbar")),
                ("failure-package.txt", ("dumpsys", "package", PRODUCT)),
                ("failure-activity.txt", ("dumpsys", "activity", "activities")),
                ("failure-logcat.txt", ("logcat", "-d", "-t", "8000")),
                ("failure-crash.txt", ("logcat", "-d", "-b", "crash")),
            ):
                try:
                    invocation = self.adb + (["shell"] if command[0] == "dumpsys" else []) + list(command)
                    collected = subprocess.run(invocation, capture_output=True, text=True,
                                               timeout=30)
                    if collected.returncode or (command[0] == "dumpsys" and not collected.stdout):
                        (self.out / name).write_text(
                            f"COLLECTION_FAILED exit={collected.returncode} command={command!r} "
                            f"empty_stdout={not collected.stdout}\n"
                            f"stderr:\n{collected.stderr}\nstdout:\n{collected.stdout}\n")
                        self.mark("diagnostic_collection_failed", {"file": name,
                                                                     "exit": collected.returncode})
                    else:
                        (self.out / name).write_text(collected.stdout)
                        self.mark("diagnostic_collected", {"file": name,
                                                           "bytes": len(collected.stdout)})
                except Exception as capture_error:
                    (self.out / name).write_text("COLLECTION_EXCEPTION " + str(capture_error) + "\n")
                    self.mark("failure_capture_error", {"file": name, "error": str(capture_error)})
            raise
        finally:
            prior_failure = sys.exc_info()[0] is not None
            video_pull_ok = False
            if self.video:
                # End the Android encoder itself so it writes the MP4 moov atom.
                for pid in self.shell("pidof", "screenrecord", check=False).split():
                    self.shell("kill", "-2", pid, check=False)
                try:
                    self.video.communicate(timeout=10)
                except subprocess.TimeoutExpired:
                    self.video.kill()
                    self.video.communicate()
                pull = subprocess.run(self.adb + ["pull", self.remote_video,
                                                  str(self.out / "first-run.mp4")],
                                      capture_output=True, text=True, timeout=30)
                video_pull_ok = (pull.returncode == 0 and (self.out / "first-run.mp4").is_file()
                                 and (self.out / "first-run.mp4").stat().st_size > 0)
                self.assertions["current recording pulled successfully"] = video_pull_ok
                if not video_pull_ok:
                    self.mark("video_pull_failure", {"returncode": pull.returncode,
                                                     "stderr": pull.stderr[-500:]})
                self.shell("rm", self.remote_video, check=False)
            if not self.args.policy_diagnostic_tall:
                self.shell("wm", "size", "reset", check=False)
            self.stop_observer()
            video_valid = video_pull_ok and self.finalized_mp4(self.out / "first-run.mp4")
            self.assertions["finalized screen recording"] = video_valid
            captures = {str(p.relative_to(self.out)): hashlib.sha256(p.read_bytes()).hexdigest()
                        for p in self.out.rglob("*") if p.is_file()
                        and p.suffix in (".png", ".jpg", ".mp4") and p.stat().st_size}
            (self.out / "evidence.json").write_text(json.dumps({
                "source": self.source,
                "events": self.events, "assertions": self.assertions,
                "capture_sha256": captures,
            }, indent=2) + "\n")
            if not video_valid and not prior_failure:
                raise AssertionError("Screen recording did not finalize; first-run.mp4 is not a valid MP4")
            if (self.observer and not self.assertions["persistent UI observer finished cleanly"]
                    and not prior_failure):
                raise AssertionError("Persistent UI observer did not finish cleanly")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--reset-test-packages", action="store_true")
    parser.add_argument("--app-apk", type=Path, required=True)
    parser.add_argument("--fixture-apk", type=Path, required=True)
    parser.add_argument("--app-commit", required=True)
    parser.add_argument("--policy-diagnostic-tall", action="store_true")
    parser.add_argument("--output", type=Path, default=ROOT / "build/store-demo")
    Demo(parser.parse_args()).run()


if __name__ == "__main__":
    main()
