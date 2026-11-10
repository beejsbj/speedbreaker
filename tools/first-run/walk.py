"""Walk a fresh install the way a new person would, on a disposable emulator.

Installs the given APK with no data, then goes through first open, declining and
reopening, consent, turning the service on in Android's own Accessibility screens,
the Phone permission (denied once, then allowed), choosing one app, and a first breath.
Each step saves a screenshot; the visible text of each screen that could be read is
saved beside it. The walk fails when a step a new person depends on does not happen.

Reading a screen uses `uiautomator dump`, which switches Accessibility services off
while it runs. Once Speedbreaker's service is on, the walk therefore reads only system
screens and the app picker, and presses the app's one filled button by finding it in
the screenshot instead.
"""
import argparse
import io
import json
import re
import shlex
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image

PACKAGE = "dev.burooj.speedbreaker"
ACTIVITY = f"{PACKAGE}/.MainActivity"
SERVICE = f"{PACKAGE}/{PACKAGE}.observation.SpeedbreakerService"
# The filled button's colour (Palette.NightIvory); the app is always dark.
FILLED = (0xEC, 0xE5, 0xD6)
# Harmless apps an emulator image usually carries, as (picker label, package).
TARGETS = [
    ("Clock", "com.google.android.deskclock"),
    ("Calendar", "com.google.android.calendar"),
    ("Files", "com.google.android.documentsui"),
    ("Contacts", "com.google.android.contacts"),
    ("Clock", "com.android.deskclock"),
    ("Calendar", "com.android.calendar"),
    ("Contacts", "com.android.contacts"),
]


class Walk:
    def __init__(self, args):
        self.adb = ["adb", "-s", args.serial]
        self.apk = args.apk
        self.out = args.output.resolve()
        self.out.mkdir(parents=True, exist_ok=True)
        self.steps = []
        self.count = 0

    # ---------- adb ----------

    def command(self, *args, check=True, binary=False, timeout=60):
        result = subprocess.run(self.adb + list(args), capture_output=True, text=not binary, timeout=timeout)
        if check and result.returncode:
            raise RuntimeError(f"adb {shlex.join(args)} failed: {result.stderr!r} {result.stdout!r}")
        return result.stdout if binary else result.stdout.strip()

    def shell(self, *args, **kwargs):
        return self.command("shell", shlex.join(args), **kwargs)

    def validate_emulator(self):
        if self.shell("getprop", "ro.kernel.qemu") != "1":
            raise RuntimeError("Refusing to run: this clears app data and changes settings; use an emulator.")
        if int(self.shell("getprop", "ro.build.version.sdk")) < 33:
            raise RuntimeError("Use an API 33+ emulator; the walk expects the notification permission.")

    # ---------- reading and pressing ----------

    def nodes(self):
        """Every on-screen node with text or a description, as (label, centre, class)."""
        for _ in range(4):
            raw = self.command("exec-out", "uiautomator", "dump", "/dev/tty", check=False)
            start, end = raw.find("<?xml"), raw.rfind("</hierarchy>")
            if start >= 0 and end > start:
                found = []
                for node in ET.fromstring(raw[start:end + len("</hierarchy>")]).iter("node"):
                    label = node.get("text") or node.get("content-desc") or ""
                    box = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
                    if box:
                        left, top, right, bottom = map(int, box.groups())
                        found.append((label, ((left + right) // 2, (top + bottom) // 2), node.get("class", "")))
                return found
            time.sleep(1)
        raise RuntimeError("Could not read the screen.")

    def texts(self):
        return [label for label, _, _ in self.nodes() if label.strip()]

    def find(self, pattern, scrolls=0):
        for attempt in range(scrolls + 1):
            for label, centre, _ in self.nodes():
                if re.search(pattern, label):
                    return centre
            if attempt < scrolls:
                self.scroll()
        return None

    def tap(self, pattern, scrolls=0, wait=1.5):
        centre = self.find(pattern, scrolls)
        if centre is None:
            raise RuntimeError(f"Nothing on screen matches {pattern!r}. Visible: {self.texts()}")
        self.shell("input", "tap", str(centre[0]), str(centre[1]))
        time.sleep(wait)

    def scroll(self):
        width, height = self.screen_size()
        self.shell("input", "swipe", str(width // 2), str(height * 7 // 10), str(width // 2), str(height * 3 // 10), "300")
        time.sleep(1)

    def screen_size(self):
        match = re.search(r"(\d+)x(\d+)", self.shell("wm", "size"))
        return int(match.group(1)), int(match.group(2))

    def screenshot(self):
        return Image.open(io.BytesIO(self.command("exec-out", "screencap", "-p", binary=True))).convert("RGB")

    def filled_buttons(self, image):
        """Filled buttons as (left, top, right, bottom): bands of rows sharing one long run of the fill colour."""
        width, height = image.size
        pixels = image.load()
        near = lambda p: all(abs(p[i] - FILLED[i]) <= 6 for i in range(3))
        bands, current = [], None
        for y in range(0, height, 2):
            best, run_start = None, None
            for x in range(0, width + 1, 2):
                if x < width and near(pixels[x, y]):
                    run_start = x if run_start is None else run_start
                elif run_start is not None:
                    if best is None or x - run_start > best[1] - best[0]:
                        best = (run_start, x)
                    run_start = None
            long_run = best if best and best[1] - best[0] >= width * 0.25 else None
            if long_run and current and long_run[0] < current[2] and long_run[1] > current[0]:
                current = [min(current[0], long_run[0]), current[1], max(current[2], long_run[1]), y]
            else:
                if current and current[3] - current[1] >= 60:
                    bands.append(tuple(current))
                current = [long_run[0], y, long_run[1], y] if long_run else None
        if current and current[3] - current[1] >= 60:
            bands.append(tuple(current))
        return bands

    def tap_filled(self, wait=2.0):
        """Press the screen's one filled button, without reading the screen."""
        buttons = self.filled_buttons(self.screenshot())
        if not buttons:
            raise RuntimeError("No filled button on screen.")
        left, top, right, bottom = buttons[0]
        self.shell("input", "tap", str((left + right) // 2), str((top + bottom) // 2))
        time.sleep(wait)

    def shot(self, name, note, read=True):
        """Save what the screen shows now. [read] is off wherever reading would disturb the service."""
        self.count += 1
        stem = f"{self.count:02d}-{name}"
        self.screenshot().save(self.out / f"{stem}.png")
        step = {"step": stem, "note": note}
        if read:
            step["text"] = self.texts()
        self.steps.append(step)
        print(f"{stem}: {note}", flush=True)
        return step

    def log(self):
        return self.command("logcat", "-d", "-s", "Speedbreaker:I", check=False)

    def wait_for_log(self, phrase, seconds):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            if phrase in self.log():
                return True
            time.sleep(0.5)
        return False

    def foreground(self):
        return self.shell("dumpsys", "activity", "activities", check=False)

    def open_app(self, wait=3.0):
        self.shell("am", "start", "-n", ACTIVITY)
        time.sleep(wait)

    def expect(self, condition, message):
        if not condition:
            raise RuntimeError(message)

    # ---------- the walk ----------

    def run(self):
        self.validate_emulator()
        self.shell("settings", "put", "secure", "enabled_accessibility_services", "null")
        self.command("uninstall", PACKAGE, check=False)
        self.command("install", str(self.apk), timeout=180)
        self.command("logcat", "-c", check=False)
        try:
            self.first_open()
            self.accessibility()
            self.phone_permission()
            target = self.choose_app()
            self.first_breath(target)
        finally:
            (self.out / "speedbreaker-log.txt").write_text(self.log() + "\n")
            (self.out / "walk.json").write_text(json.dumps(self.steps, indent=2) + "\n")
            self.shell("settings", "put", "secure", "enabled_accessibility_services", "null", check=False)

    def first_open(self):
        self.open_app(wait=6)
        first = self.shot("first-open", "The first thing a new person sees.")
        self.expect(any("Speedbreaker" in text for text in first["text"]), "First open does not name the app.")
        self.scroll()
        self.shot("first-open-scrolled", "The same screen, scrolled.")
        self.tap(r"^Not now", scrolls=3)
        time.sleep(1)
        self.expect(PACKAGE not in self.resumed(), "Declining did not close the app.")
        self.open_app(wait=4)
        self.expect(self.find(r"I understand", scrolls=3) is not None, "After declining, reopening skipped the disclosure.")
        self.shot("reopened-after-decline", "Declined, then reopened: the disclosure is back.")
        self.tap(r"I understand", scrolls=3, wait=3)
        self.shot("home-before-setup", "Home straight after accepting.")
        self.shell("am", "force-stop", PACKAGE)
        self.open_app(wait=4)
        self.expect(self.find(r"I understand") is None, "The disclosure showed again after it was accepted.")

    def resumed(self):
        return "\n".join(line for line in self.foreground().splitlines() if "ResumedActivity" in line)

    def accessibility(self):
        self.tap(r"Turn on in Accessibility", scrolls=2, wait=3)
        self.shot("android-accessibility", "Android's Accessibility screen, where the button leads.")
        how = "android-settings"
        try:
            self.tap(r"Speedbreaker attention pauses", scrolls=6, wait=2)
            self.shot("android-service-page", "Speedbreaker's entry in Android settings.")
            self.tap(r"^Use Speedbreaker attention pauses", wait=2)
            self.shot("android-warning", "Android's standard warning for every Accessibility service.")
            self.tap(r"^Allow$", wait=2)
        except RuntimeError as error:
            # Settings layouts differ by image; the rest of the walk still needs the service on.
            print(f"Could not turn the service on through Settings ({error}); using adb.", flush=True)
            how = "adb"
            self.shell("settings", "put", "secure", "enabled_accessibility_services", SERVICE)
        self.steps.append({"step": "service-enabled", "note": f"Turned on through: {how}"})
        self.expect(PACKAGE in self.shell("settings", "get", "secure", "enabled_accessibility_services"),
                    "The service is not enabled.")
        self.expect(self.wait_for_log("service connected", 30), "The service never connected.")

    def phone_permission(self):
        self.open_app()
        self.shot("phone-step", "Back in the app with the service on: the Phone permission step.", read=False)
        self.tap_filled()
        self.shot("android-phone-prompt", "Android's Phone permission prompt.")
        self.tap(r"^Don.t allow$", wait=4)
        self.shot("phone-denied", "After saying no to Phone.", read=False)
        self.tap_filled()
        if self.find(r"^Allow$") is None:
            self.shot("phone-denied-second-press", "The same button after a denial, when Android does not ask again.")
            self.shell("pm", "grant", PACKAGE, "android.permission.READ_PHONE_STATE")
            self.open_app()
        else:
            self.tap(r"^Allow$", wait=4)
        self.expect("granted=true" in self.permission_line("READ_PHONE_STATE"), "Phone permission was not granted.")

    def permission_line(self, name):
        dump = self.shell("dumpsys", "package", PACKAGE, check=False)
        return "\n".join(line for line in dump.splitlines() if f"android.permission.{name}:" in line)

    def choose_app(self):
        self.open_app()
        self.shot("ready-to-choose", "Service on and Phone allowed: choosing apps is next.", read=False)
        self.tap_filled()
        picker = self.shot("app-picker", "The app picker.")
        target = next(((label, package) for label, package in TARGETS
                       if self.shell("pm", "path", package, check=False)
                       and any(text == label for text in picker["text"])), None)
        if target is None:
            for label, package in TARGETS:
                if self.shell("pm", "path", package, check=False) and self.find(f"^{label}$", scrolls=6):
                    target = (label, package)
                    break
        self.expect(target is not None, f"No known harmless app in the picker: {picker['text']}")
        self.tap(f"^{target[0]}$", scrolls=6)
        self.shot("app-chosen", f"{target[0]} chosen.")
        self.shell("input", "keyevent", "KEYCODE_BACK")
        # Reading the picker switched the service off for a moment; give it time to return.
        time.sleep(6)
        self.shot("home-on", "Home with setup finished.", read=False)
        return target

    def first_breath(self, target):
        label, package = target
        self.shell("input", "keyevent", "KEYCODE_HOME")
        time.sleep(2)
        self.command("logcat", "-c", check=False)
        self.shell("monkey", "-p", package, "-c", "android.intent.category.LAUNCHER", "1", check=False)
        self.expect(self.wait_for_log("overlay addView ok", 30), f"Opening {label} did not start a breath.")
        shown = time.monotonic()
        time.sleep(2)
        self.shot("breath", f"Opening {label}: the breath.", read=False)
        time.sleep(max(0.0, 9.5 - (time.monotonic() - shown)))
        choices = self.screenshot()
        self.count += 1
        choices.save(self.out / f"{self.count:02d}-choices.png")
        self.steps.append({"step": f"{self.count:02d}-choices", "note": "After eight seconds: the choices."})
        buttons = self.filled_buttons(choices)
        self.expect(buttons, "No choice was available after eight seconds.")
        # Before the breath ends Continue is not filled, so the filled button is Leave.
        left, top, right, bottom = buttons[0]
        self.shell("input", "tap", str((left + right) // 2), str((top + bottom) // 2))
        self.expect(self.wait_for_log("overlay dismissed", 15), "Leave did not end the breath.")
        time.sleep(2)
        self.shot("after-leave", "After Leave.", read=False)
        self.open_app()
        self.shot("home-after-first-breath", "Home after the first breath.", read=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", required=True, help="emulator serial, for example emulator-5554")
    parser.add_argument("--apk", required=True, type=Path, help="an installable (signed) Speedbreaker APK")
    parser.add_argument("--output", type=Path, default=Path("build/first-run"))
    args = parser.parse_args()
    walk = Walk(args)
    try:
        walk.run()
    except RuntimeError as error:
        try:
            walk.shot("failed", f"Stopped here: {error}", read=False)
            (walk.out / "walk.json").write_text(json.dumps(walk.steps, indent=2) + "\n")
        except Exception:
            pass
        print(f"First-run walk failed: {error}", file=sys.stderr)
        return 1
    print(f"First-run walk passed: {len(walk.steps)} steps in {walk.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
