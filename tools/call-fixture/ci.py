"""Keep workflow input out of shell interpolation; the AVD serial is explicit."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys

community_apk = Path("source-app/app/build/outputs/apk/community/debug/app-community-debug.apk")
play_apk = Path("source-app/app/build/outputs/apk/play/debug/app-play-debug.apk")
plain_apk = Path("source-app/app/build/outputs/apk/debug/app-debug.apk")
if community_apk.is_file() and play_apk.is_file() and not plain_apk.exists():
    regression_apk = community_apk
    demo_apk = play_apk
elif plain_apk.is_file() and not community_apk.exists() and not play_apk.exists():
    regression_apk = plain_apk
    demo_apk = None
else:
    raise SystemExit("Expected CommunityDebug + PlayDebug, or one historical plain Debug APK")
app_apk = str(regression_apk)
fixture_apk = "tools/call-fixture/build/outputs/apk/debug/call-fixture-debug.apk"
app_commit = subprocess.check_output(["git", "-C", "source-app", "rev-parse", "HEAD"], text=True).strip()
command = [sys.executable, "tools/call-fixture/run.py", "--serial", "emulator-5554", "--reset-test-packages",
           "--app-apk", app_apk, "--app-commit", app_commit]
baseline = os.environ.get("BASELINE_WITHOUT_PHONE_PERMISSION") == "true"
demo_only = os.environ.get("DEMO_ONLY") == "true"
if demo_only:
    if baseline or demo_apk is None or os.environ.get("RUN_STORE_DEMO") != "true" or os.environ.get("GITHUB_EVENT_NAME") != "workflow_dispatch":
        raise SystemExit("demo_only requires manual dispatch, flavored Play APK, RUN_STORE_DEMO, and no baseline")
    skipped = Path("build/device-regression")
    skipped.mkdir(parents=True, exist_ok=True)
    (skipped / "skipped.json").write_text(json.dumps({
        "status": "SKIPPED_DIAGNOSTIC_ONLY", "reason": "manual demo_only dispatch",
        "app_commit": app_commit, "regression_apk": app_apk,
        "api": subprocess.check_output(["adb", "-s", "emulator-5554", "shell", "getprop", "ro.build.version.sdk"], text=True).strip(),
    }, indent=2) + "\n")
    print("CALL_REGRESSION_SKIPPED_DIAGNOSTIC_ONLY", flush=True)
    regression_code = 0
elif baseline:
    # The existing run.py grants Phone permission in prepare(). Revoke it only
    # for the explicit pre-fix control, after every per-scenario reset/setup.
    spec = importlib.util.spec_from_file_location("call_fixture_run", "tools/call-fixture/run.py")
    fixture_run = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(fixture_run)

    class WithoutPhonePermission(fixture_run.Harness):
        def prepare(self, directory):
            super().prepare(directory)
            state = self.shell("dumpsys", "package", fixture_run.PRODUCT)
            declared = any(line.strip() == "android.permission.READ_PHONE_STATE"
                           for line in state.splitlines())
            if declared:
                self.shell("pm", "revoke", fixture_run.PRODUCT,
                           "android.permission.READ_PHONE_STATE")
                state = self.shell("dumpsys", "package", fixture_run.PRODUCT)
                print("NEGATIVE_CONTROL_PHONE_REVOKED", flush=True)
            else:
                print("NEGATIVE_CONTROL_PHONE_NOT_DECLARED", flush=True)
            if "android.permission.READ_PHONE_STATE: granted=true" in state:
                raise AssertionError("Negative control still has Phone permission")

    args = argparse.Namespace(serial="emulator-5554", reset_test_packages=True,
                              app_apk=Path(app_apk), app_commit=app_commit,
                              fixture_apk=Path(fixture_apk), output=Path("build/device-regression"),
                              scenario="all", baseline_without_phone_permission=True)
    try:
        WithoutPhonePermission(args).run()
        print("NEGATIVE_CONTROL_UNEXPECTED_PASS: calls passed without Phone permission",
              file=sys.stderr)
        regression_code = 1
    except Exception as error:
        evidence = Path("build/device-regression")
        failed_calls = [name for name in ("ringing", "silent")
                        if (evidence / name / "instrumentation.txt").is_file()
                        and "INSTRUMENTATION_RESULT: result=FAIL" in
                        (evidence / name / "instrumentation.txt").read_text()
                        and (evidence / name / "host-events.json").is_file()
                        and any(event.get("event") == "gsm_call_sent"
                                for event in json.loads((evidence / name / "host-events.json").read_text()))]
        if failed_calls:
            print(f"NEGATIVE_CONTROL_CALL_FAILURE_AFTER_INJECTION: {failed_calls}; {error}",
                  file=sys.stderr)
        else:
            print(f"NEGATIVE_CONTROL_SETUP_OR_OTHER_FAILURE: {error}", file=sys.stderr)
        regression_code = 1
else:
    regression_code = subprocess.run(command, check=False).returncode
demo = None
if (os.environ.get("RUN_STORE_DEMO") == "true"
        and not baseline
        and demo_apk is not None):
    # run.py owns its own consent seed. This separate run clears both packages again.
    demo = subprocess.run([sys.executable, "tools/call-fixture/store_demo.py",
                           "--serial", "emulator-5554", "--reset-test-packages",
                           "--app-apk", str(demo_apk), "--fixture-apk", fixture_apk,
                           "--app-commit", app_commit], check=False)
if regression_code or (demo is not None and demo.returncode):
    raise SystemExit(f"device checks failed: regression={regression_code}, "
                     f"store_demo={demo.returncode if demo is not None else 'skipped'}")
