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
# The CI entrypoint resolves the installed host's data container once, right after
# `simctl install`, and exports it here so per-test phases do not repeat the lookup.
CONTAINER_ENV = "IOS_RECOVERY_APP_CONTAINER"
# One-off lookup outside any phase budget. On a freshly booted simulator CoreSimulator
# can take minutes to answer get_app_container (CI saw ~175 s for a single call).
CONTAINER_DISCOVERY_TIMEOUT = 600
# A fallback lookup inside a phase may use the shared budget, but never all of it:
# this much is always left for `simctl launch` and the test itself.
MIN_LAUNCH_WINDOW = 60


def simctl(*arguments, env=None, timeout=30):
    try:
        result = subprocess.run(["xcrun", "simctl", *arguments], env=env, text=True,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=timeout)
    except subprocess.TimeoutExpired as error:
        output = error.output or b""
        if isinstance(output, bytes):
            output = output.decode(errors="replace")
        raise RuntimeError(f"simctl {arguments[0]} timed out after {timeout:g} seconds\n{output}") from error
    if result.returncode:
        raise RuntimeError(result.stdout)
    return result.stdout


def require_exact_device(device):
    if device in ("booted", "all"):
        raise ValueError("Select an exact simulator UDID")


def resolve_container(device, timeout=CONTAINER_DISCOVERY_TIMEOUT):
    """Look up the installed host's data container once, with its own generous timeout."""
    require_exact_device(device)
    return Path(simctl("get_app_container", device, PACKAGE, "data", timeout=timeout).strip())


def cached_container(device):
    """Return the pre-resolved container if it belongs to this simulator and still exists."""
    value = os.environ.get(CONTAINER_ENV, "").strip()
    if not value:
        return None
    container = Path(value)
    # A container from another simulator, or one replaced by an uninstall, is never
    # reused; the caller falls back to a fresh lookup.
    if device not in container.parts or not container.is_dir():
        return None
    return container


def run_test(device, arguments, timeout=180):
    require_exact_device(device)
    run = str(uuid.uuid4())
    # Container discovery also waits on CoreSimulator during a cold boot. Give
    # discovery, launch and test execution one budget, without resetting it.
    deadline = time.monotonic() + timeout
    container = cached_container(device)
    if container is None:
        # Fallback lookup stays inside the phase budget but cannot starve launch:
        # previously a 175 s lookup left 4.6 s for `simctl launch`, which then
        # timed out with a misleading error. Fail on discovery itself instead.
        discovery_timeout = timeout - min(MIN_LAUNCH_WINDOW, timeout / 2)
        container = Path(simctl("get_app_container", device, PACKAGE, "data", timeout=discovery_timeout).strip())
    log = container / "Documents" / (run + ".log")
    launch_timeout = deadline - time.monotonic()
    if launch_timeout <= 0:
        raise RuntimeError(f"Recovery host exhausted its {timeout:g}-second budget during container discovery")
    launch = ""
    failure = None
    stopped = False
    try:
        launch = simctl("launch", "--terminate-running-process", device, PACKAGE, *arguments,
                        env=dict(os.environ, SIMCTL_CHILD_RECOVERY_HOST_RUN=run), timeout=launch_timeout)
        launched = re.search(r"^" + re.escape(PACKAGE) + r": (\d+)\s*$", launch, re.MULTILINE)
        if not launched or int(launched[1]) <= 1:
            raise RuntimeError("Simulator did not acknowledge a recovery host PID")
        while True:
            output = log.read_text(errors="replace") if log.exists() else ""
            pid = re.search(r"^RECOVERY_HOST_PID=(\d+)\n", output, re.MULTILINE)
            if pid and pid[1] != launched[1]:
                raise RuntimeError("Recovery host PID does not match the acknowledged launch")
            completed = re.search(r"^RECOVERY_TEST_EXIT=(-?\d+)\n", output, re.MULTILINE)
            if completed:
                if completed[1] != "0":
                    failure = "Recovery host reported a nonzero test exit"
                elif any(arg.startswith("--swiftRecoveryExchange=") for arg in arguments) and "RECOVERY_INTEROP_EXIT=0\n" not in output:
                    failure = "Swift recovery exchange did not report successful completion"
                if not pid:
                    raise RuntimeError("Recovery host completed without identifying its PID")
                Path(str(log) + ".release").touch()
                wait_for_exit(int(pid[1]))
                stopped = True
                break
            if pid and not process_is_running(int(pid[1])):
                stopped = True
                failure = "Recovery host exited without reporting completion"
                break
            if time.monotonic() >= deadline:
                failure = f"Recovery host did not report completion within {timeout:g} seconds (including container discovery and launch)"
                break
            time.sleep(0.25)
    except (RuntimeError, OSError) as error:
        failure = (failure + "\n" if failure else "") + str(error)
    finally:
        # Incomplete/crashed launches cannot use the completion handshake. Failure
        # cleanup must never turn their missing evidence into a passing phase.
        if not stopped:
            try:
                simctl("terminate", device, PACKAGE)
            except (RuntimeError, OSError) as error:
                failure = (failure + "\n" if failure else "") + "Host cleanup failed: " + str(error)
    if stopped:
        with log.open("a") as stream:
            stream.write(f"RECOVERY_HOST_STOPPED={pid[1]}\n")
    output = log.read_text(errors="replace") if log.exists() else ""
    if failure:
        raise RuntimeError(launch + output + "\n" + failure)
    return output


def process_is_running(pid):
    try:
        os.kill(pid, 0)  # Simulator apps are local processes; this only probes existence.
        return True
    except ProcessLookupError:
        return False


def wait_for_exit(pid, timeout=30):
    deadline = time.monotonic() + timeout
    while process_is_running(pid):
        if time.monotonic() >= deadline:
            raise RuntimeError(f"Recovery host {pid} did not exit after release")
        time.sleep(0.1)


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
