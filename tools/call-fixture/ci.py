"""Keep workflow input out of shell interpolation; the AVD serial is explicit."""
import os
import subprocess
import sys

command = [sys.executable, "tools/call-fixture/run.py", "--serial", "emulator-5554", "--reset-test-packages",
           "--app-apk", "source-app/app/build/outputs/apk/debug/app-debug.apk", "--app-commit",
           subprocess.check_output(["git", "-C", "source-app", "rev-parse", "HEAD"], text=True).strip()]
if os.environ.get("BASELINE_WITHOUT_PHONE_PERMISSION") == "true":
    command.append("--baseline-without-phone-permission")
subprocess.run(command, check=True)
