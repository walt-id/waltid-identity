"""Run one Kotlin test in the isolated recovery host and require explicit completion."""
import argparse
import os
from pathlib import Path
import re
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

PACKAGE = "id.walt.wallet.recovery-tests"


def simctl(*arguments, env=None):
    result = subprocess.run(["xcrun", "simctl", *arguments], env=env, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=30)
    if result.returncode:
        raise RuntimeError(result.stdout)
    return result.stdout


def run_test(device, arguments, timeout=180):
    if device in ("booted", "all"):
        raise ValueError("Select an exact simulator UDID")
    run = str(uuid.uuid4())
    container = Path(simctl("get_app_container", device, PACKAGE, "data").strip())
    log = container / "Documents" / (run + ".log")
    launch = simctl("launch", "--terminate-running-process", device, PACKAGE, *arguments,
                    env=dict(os.environ, SIMCTL_CHILD_RECOVERY_HOST_RUN=run))
    deadline = time.monotonic() + timeout
    output = ""
    while True:
        if log.exists():
            output = log.read_text(errors="replace")
            completed = re.search(r"^RECOVERY_HOST_EXIT=(-?\d+)\n", output, re.MULTILINE)
            if completed:
                if completed[1] != "0":
                    raise RuntimeError(launch + output)
                return output
        if time.monotonic() >= deadline:
            try:
                simctl("terminate", device, PACKAGE)
            except (RuntimeError, OSError, subprocess.TimeoutExpired):
                pass  # A crashed process cannot be terminated; retain its partial output below.
            raise RuntimeError(launch + output + "\nRecovery host did not report completion before timeout")
        time.sleep(0.25)


def passed_one_test(output):
    return "[  PASSED  ] 1 tests." in output and "[  FAILED  ]" not in output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("arguments", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    arguments = args.arguments[1:] if args.arguments[:1] == ["--"] else args.arguments
    try:
        output = run_test(args.device, arguments)
        passed = passed_one_test(output)
    except (RuntimeError, ValueError, OSError, subprocess.TimeoutExpired) as error:
        output, passed = str(error), False
    (args.output / "keychain-contract.log").write_text(output)
    suite = ET.Element("testsuite", name="recovery.keychain", tests="1", failures="0" if passed else "1")
    case = ET.SubElement(suite, "testcase", classname="recovery.keychain", name="record-contract")
    if not passed:
        ET.SubElement(case, "failure", message="Expected one completed, passing Keychain contract; see keychain-contract.log")
    ET.ElementTree(suite).write(args.output / "results.xml", encoding="utf-8", xml_declaration=True)
    if not passed:
        raise SystemExit("Keychain contract failed or produced incomplete results")


if __name__ == "__main__":
    main()
