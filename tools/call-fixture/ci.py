"""Keep workflow input out of shell interpolation; the AVD serial is explicit."""
import os
import subprocess
import sys

command = [sys.executable, "tools/call-fixture/run.py", "--serial", "emulator-5554", "--reset-test-packages"]
if os.environ.get("BASELINE_WITHOUT_PHONE_PERMISSION") == "true":
    command.append("--baseline-without-phone-permission")
subprocess.run(command, check=True)
