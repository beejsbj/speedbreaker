"""Keep workflow input out of shell interpolation; the AVD serial is explicit."""
import argparse
import importlib.util
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
baseline = os.environ.get("BASELINE_WITHOUT_PHONE_PERMISSION") == "true"
if baseline:
    # The existing run.py grants Phone permission in prepare(). Revoke it only
    # for the explicit pre-fix control, after every per-scenario reset/setup.
    spec = importlib.util.spec_from_file_location("call_fixture_run", "tools/call-fixture/run.py")
    fixture_run = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(fixture_run)

    class WithoutPhonePermission(fixture_run.Harness):
        def prepare(self, directory):
            super().prepare(directory)
            self.shell("pm", "revoke", fixture_run.PRODUCT,
                       "android.permission.READ_PHONE_STATE")
            state = self.shell("dumpsys", "package", fixture_run.PRODUCT)
            if "android.permission.READ_PHONE_STATE: granted=true" in state:
                raise AssertionError("Negative control still has Phone permission")
            print("NEGATIVE_CONTROL_PHONE_REVOKED", flush=True)

    args = argparse.Namespace(serial="emulator-5554", reset_test_packages=True,
                              app_apk=Path(app_apk), app_commit=app_commit,
                              fixture_apk=Path(fixture_apk), output=Path("build/device-regression"),
                              scenario="all", baseline_without_phone_permission=True)
    try:
        WithoutPhonePermission(args).run()
        print("Negative control unexpectedly passed without Phone permission", file=sys.stderr)
        regression_code = 1
    except Exception as error:
        print(f"Negative control regression failed: {error}", file=sys.stderr)
        regression_code = 1
else:
    regression_code = subprocess.run(command, check=False).returncode
demo = None
if (os.environ.get("RUN_STORE_DEMO") == "true"
        and not baseline
        and found[0] == community_apk):
    # run.py owns its own consent seed. This separate run clears both packages again.
    demo = subprocess.run([sys.executable, "tools/call-fixture/store_demo.py",
                           "--serial", "emulator-5554", "--reset-test-packages",
                           "--app-apk", app_apk, "--fixture-apk", fixture_apk,
                           "--app-commit", app_commit], check=False)
if regression_code or (demo is not None and demo.returncode):
    raise SystemExit(f"device checks failed: regression={regression_code}, "
                     f"store_demo={demo.returncode if demo is not None else 'skipped'}")
