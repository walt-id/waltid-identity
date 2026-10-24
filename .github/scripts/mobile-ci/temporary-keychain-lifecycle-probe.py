"""Temporary WAL-1585 capture: stop at the first failed independent launch."""
import hashlib
import json
import os
from pathlib import Path
import signal
import shutil
import subprocess
import time

root = Path(os.environ["GITHUB_WORKSPACE"]) / "waltid-identity"
out = root / "build/reports/ios-wallet-recovery/cold-lifetimes"
out.mkdir(parents=True, exist_ok=False)
scripts = root / "waltid-libraries/crypto/waltid-crypto2-signum/scripts"
record = {"identity": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip(),
          "xcode": subprocess.check_output(["xcodebuild", "-version"], text=True).strip(),
          "maximum_lifetimes": 20, "attempts": []}
destination = dict(part.split("=", 1) for part in os.environ["IOS_SIMULATOR_DESTINATION"].split(","))
assert destination["platform"] == "iOS Simulator" and set(destination) == {"platform", "name", "OS"}
record["destination"] = destination
record["runtimes"] = json.loads(subprocess.check_output(["xcrun", "simctl", "list", "runtimes", "--json"], text=True))["runtimes"]
active = None


def save():
    (out / "state.json").write_text(json.dumps(record, indent=2) + "\n")


def kill_owned_child():
    if active is not None:
        try:
            os.killpg(active.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass


def cancel(signum, _frame):
    kill_owned_child()
    record["cancelled_by_signal"] = signum
    save()
    raise SystemExit(128 + signum)


for signum in (signal.SIGINT, signal.SIGTERM):
    signal.signal(signum, cancel)


def run(args, log, timeout=180, env=None):
    global active
    start = time.monotonic()
    with (out / log).open("w") as stream:
        active = subprocess.Popen(args, cwd=root, stdout=stream, stderr=subprocess.STDOUT, start_new_session=True, env=env)
        try:
            code = active.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            kill_owned_child()
            active.wait()
            code = 124
        finally:
            active = None
    return {"returncode": code, "seconds": round(time.monotonic() - start, 3), "log": log}


# Reuse the actual script through signing; no test/compiler/entitlement changes.
source = scripts / "test-ios-keychain.sh"
prefix, separator, _ = source.read_text().partition('\nxcrun simctl bootstatus "$simulator_id" -b\n')
assert separator and "codesign --force --sign" in prefix
observer = root / ".github/scripts/mobile-ci/temporary-keychain-observed-main.c"
obj = out / "observed-main.o"
sdk = subprocess.check_output(["xcrun", "--sdk", "iphonesimulator", "--show-sdk-path"], text=True, timeout=30).strip()
record["observer_compile"] = run(["xcrun", "--sdk", "iphonesimulator", "clang", "-target", "arm64-apple-ios16.0-simulator",
                                  "-isysroot", sdk, "-Wall", "-Wextra", "-Werror", "-c", str(observer), "-o", str(obj)], "observer-compile.log")
if record["observer_compile"]["returncode"]:
    save()
    raise SystemExit(record["observer_compile"]["returncode"])
linker = '"-L" + System.getenv("KEYCHAIN_TEST_SWIFT_LIBS"))'
assert prefix.count(linker) == 1
prefix = prefix.replace(linker, '"-L" + System.getenv("KEYCHAIN_TEST_SWIFT_LIBS"), System.getenv("WAL1585_MARKER_OBJECT"), "-e", "_keychainObservedMain")')
record["builder_sha256"] = hashlib.sha256(source.read_bytes()).hexdigest()
record["runner_sha256"] = hashlib.sha256((scripts / "run_keychain_tests.py").read_bytes()).hexdigest()
builder = scripts / ".temporary-keychain-build.sh"
assert not builder.exists()
try:
    builder.write_text(prefix + "\n")
    record["build"] = run(["bash", str(builder), "not-used", str(out / "bootstrap")], "build.log", 1200, env={**os.environ, "WAL1585_MARKER_OBJECT": str(obj)})
finally:
    builder.unlink()
    save()
if record["build"]["returncode"]:
    raise SystemExit(record["build"]["returncode"])
app = out / "bootstrap/KeychainTests.app"
record["executable_sha256"] = hashlib.sha256((app / "KeychainTests").read_bytes()).hexdigest()
save()

for number in range(1, 21):
    name = f"cold-{number:02d}"
    attempt = {"name": name}
    record["attempts"].append(attempt)
    simulator = subprocess.check_output(["xcrun", "simctl", "create", f"WAL1585 {name}",
                                        destination["name"], "iOS " + destination["OS"]], text=True, timeout=30).strip()
    attempt["simulator"] = simulator
    save()
    marker = None
    try:
        attempt["boot"] = run(["xcrun", "simctl", "bootstatus", simulator, "-b"], f"{name}-boot.log", timeout=None)
        if attempt["boot"]["returncode"]:
            raise SystemExit(attempt["boot"]["returncode"])
        attempt["install"] = run(["xcrun", "simctl", "install", simulator, str(app)], f"{name}-install.log", timeout=None)
        if attempt["install"]["returncode"]:
            raise SystemExit(attempt["install"]["returncode"])
        container = subprocess.check_output(["xcrun", "simctl", "get_app_container", simulator,
                                             "id.walt.crypto2.keychain-tests", "data"], text=True, timeout=30).strip()
        marker = Path(container) / "Documents/main-return.txt"
        marker.parent.mkdir(parents=True, exist_ok=True)
        # Original runner performs all assertions and captures the first failed exit.
        attempt["test"] = run(["python3", str(scripts / "run_keychain_tests.py"), simulator,
                               str(out / name)], f"{name}-driver.log", env={**os.environ, "SIMCTL_CHILD_WAL1585_KEYCHAIN_MARKER": str(marker)})
        if not attempt["test"]["returncode"]:
            attempt["report_gate"] = run([
                "python3", str(root / ".github/scripts/mobile-ci/verify_test_results.py"),
                "--reports", str(out / name / "results.xml"),
                "--require-class", "id.walt.crypto2.signum.IosKeyOwnershipTest",
            ], f"{name}-report-gate.log")
    finally:
        if marker is not None and marker.exists():
            (out / name).mkdir(parents=True, exist_ok=True)
            shutil.copy2(marker, out / name / "main-return.log")
            attempt["marker"] = marker.read_text()
        attempt["cleanup"] = run(["xcrun", "simctl", "delete", simulator], f"{name}-cleanup.log", 90)
        save()
    print(json.dumps(attempt), flush=True)
    if (attempt["test"]["returncode"] or attempt["cleanup"]["returncode"]
            or attempt.get("report_gate", {}).get("returncode", 0)):
        raise SystemExit(1)
record["completed_lifetimes"] = len(record["attempts"])
save()
