"""Keep workflow input out of shell interpolation; the AVD serial is explicit."""
import os
from pathlib import Path
import subprocess
import sys

community_apk = Path("source-app/app/build/outputs/apk/community/debug/app-community-debug.apk")
plain_apk = Path("source-app/app/build/outputs/apk/debug/app-debug.apk")
found = [path for path in (community_apk, plain_apk) if path.is_file()]
if len(found) != 1:
    raise SystemExit(f"Expected exactly one built candidate APK; found {found}")
app_apk = str(found[0])
fixture_apk = "tools/call-fixture/build/outputs/apk/debug/call-fixture-debug.apk"
app_commit = subprocess.check_output(["git", "-C", "source-app", "rev-parse", "HEAD"], text=True).strip()
command = [sys.executable, "tools/call-fixture/run.py", "--serial", "emulator-5554", "--reset-test-packages",
           "--app-apk", app_apk, "--app-commit", app_commit]
if os.environ.get("BASELINE_WITHOUT_PHONE_PERMISSION") == "true":
    command.append("--baseline-without-phone-permission")
regression = subprocess.run(command, check=False)
demo = None
if (os.environ.get("RUN_STORE_DEMO") == "true"
        and os.environ.get("BASELINE_WITHOUT_PHONE_PERMISSION") != "true"
        and found[0] == community_apk):
    # run.py owns its own consent seed. This separate run clears both packages again.
    demo = subprocess.run([sys.executable, "tools/call-fixture/store_demo.py",
                           "--serial", "emulator-5554", "--reset-test-packages",
                           "--app-apk", app_apk, "--fixture-apk", fixture_apk,
                           "--app-commit", app_commit], check=False)
if regression.returncode or (demo is not None and demo.returncode):
    raise SystemExit(f"device checks failed: regression={regression.returncode}, "
                     f"store_demo={demo.returncode if demo is not None else 'skipped'}")
