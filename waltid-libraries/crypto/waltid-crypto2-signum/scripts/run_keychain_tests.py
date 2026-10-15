"""Run the entitled Keychain app without losing failures before simulator cleanup."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

BUNDLE = "id.walt.crypto2.keychain-tests"


def report(output, text, returncode, problem=None):
    started = re.findall(r"^\[ RUN      \] (.+)$", text, re.MULTILINE)
    passed = re.findall(r"^\[       OK \] (.+) \((\d+) ms\)$", text, re.MULTILINE)
    failed = re.findall(r"^\[  FAILED  \] (.+) \(\d+ ms\)$", text, re.MULTILINE)
    expected = re.search(r"^\[==========\] Running (\d+) tests? from", text, re.MULTILINE)
    summary = re.search(r"^\[  PASSED  \] (\d+) tests\.$", text, re.MULTILINE)
    complete = (started and expected and summary and int(expected[1]) == int(summary[1]) == len(started) == len(passed)
                and started == [name for name, _ in passed] and len(set(started)) == len(started))
    forbidden = re.search(r"^\[.*(?:FAILED|SKIPPED|DISABLED).*\]|^SKIP\b", text, re.MULTILINE)
    success = bool(returncode == 0 and not problem and complete and not forbidden)
    reason = problem or (f"simctl exited with {returncode}; {len(passed)}/{len(started)} started tests passed; "
                         f"expected {expected[1] if expected else 'unknown'}; complete summary={bool(complete)}")
    suite = ET.Element("testsuite", name="signum.keychain")
    times = dict(passed)
    incomplete = [name for name in started if name not in times and name not in failed]
    for identity in started:
        classname, _, name = identity.rpartition(".")
        case = ET.SubElement(suite, "testcase", classname=classname, name=name,
                             time=str(int(times.get(identity, "0")) / 1000))
        if identity in failed:
            ET.SubElement(case, "failure", message="Keychain test failed; see tests.log")
        elif identity in incomplete:
            # No assertion is inferred from a process disappearing mid-test.
            ET.SubElement(case, "error", message="Test did not complete; see launch.json and tests.log")
    host_error = not success and not incomplete and not failed
    if host_error:
        case = ET.SubElement(suite, "testcase", classname="signum.keychain", name="host-completion")
        ET.SubElement(case, "error", message=reason)
    suite.set("tests", str(len(suite)))
    suite.set("errors", str(len(incomplete) + int(host_error)))
    suite.set("failures", str(sum(name in failed for name in started)))
    ET.ElementTree(suite).write(output / "results.xml", encoding="utf-8", xml_declaration=True)
    return {"success": success, "simctl_returncode": returncode, "problem": None if success else reason,
            "expected": int(expected[1]) if expected else None, "started": started,
            "passed": [name for name, _ in passed], "failed": failed, "unfinished": incomplete}


def crash_diagnostics(started_at, pid=None, device=None):
    """Retain only process/termination metadata, never raw crash memory or environment."""
    records = []
    directories = [Path.home() / "Library/Logs/DiagnosticReports"]
    if device:
        directories.append(Path.home() / "Library/Developer/CoreSimulator/Devices" / device /
                           "data/Library/Logs/CrashReporter")
    paths = [path for directory in directories for pattern in ("KeychainTests*.ips", "JetsamEvent*.ips")
             for path in directory.glob(pattern)]
    for path in paths:
        try:
            if path.stat().st_mtime < started_at:
                continue
            raw = path.read_text()
            decoder = json.JSONDecoder()
            header, end = decoder.raw_decode(raw)
            body = raw[end:].strip()
            data = json.loads(body) if body else header
            if path.name.startswith("JetsamEvent"):
                # A jetsam report includes unrelated processes; retain only this
                # app when its own entry says why it was terminated.
                for process in data.get("processes", []):
                    if (process.get("name") == "KeychainTests" and process.get("reason")
                            and (pid is None or process.get("pid") == pid)):
                        records.append({"report": path.name, "pageSize": data.get("pageSize"),
                                        "jetsam": {k: process[k] for k in ("name", "pid", "reason", "rpages")
                                                   if k in process}})
                continue
            if pid is not None and data.get("pid") != pid:
                continue
            exception = data.get("exception", {})
            termination = data.get("termination", {})
            threads = data.get("threads", [])
            faulting = data.get("faultingThread", -1)
            frames = threads[faulting].get("frames", []) if isinstance(faulting, int) and 0 <= faulting < len(threads) else []
            images = data.get("usedImages", [])
            stack = []
            for frame in frames[:16]:
                index = frame.get("imageIndex", -1)
                image = images[index].get("name") if isinstance(index, int) and 0 <= index < len(images) else None
                stack.append({"image": image, **{k: frame[k] for k in ("symbol", "imageOffset") if k in frame}})
            records.append({"report": path.name, "pid": data.get("pid"),
                            "exception": {k: exception[k] for k in ("type", "signal", "codes") if k in exception},
                            "termination": {k: termination[k] for k in ("namespace", "code", "indicator", "byProc")
                                            if k in termination}, "faulting_stack": stack})
        except (OSError, ValueError, TypeError, AttributeError) as error:
            records.append({"report": path.name, "unreadable": type(error).__name__})
    return records


def run(device, output, timeout=120):
    if device in ("booted", "all"):
        raise ValueError("Select an exact simulator UDID")
    output.mkdir(parents=True, exist_ok=True)
    started_at = time.time()
    start = time.monotonic()
    code, problem = None, None
    try:
        with (output / "tests.log").open("w") as stream:
            result = subprocess.run(["xcrun", "simctl", "launch", "--terminate-running-process", "--console",
                                     device, BUNDLE, "--ktest_logger=GTEST",
                                     "--ktest_filter=*-id.walt.crypto2.signum.IosSecureEnclaveKeyTest.*"],
                                    stdout=stream, stderr=subprocess.STDOUT, timeout=timeout)
        code = result.returncode
    except subprocess.TimeoutExpired:
        problem = f"Keychain test launch exceeded {timeout} seconds"
    except OSError as error:
        problem = f"Cannot launch Keychain tests: {type(error).__name__}"
    text = (output / "tests.log").read_text(errors="replace")
    receipt = report(output, text, code, problem)
    receipt["started_at_unix"] = started_at
    receipt["elapsed_seconds"] = round(time.monotonic() - start, 3)
    launched = re.search(r"^" + re.escape(BUNDLE) + r": (\d+)\s*$", text, re.MULTILINE)
    if launched:
        receipt["pid"] = int(launched[1])
        try:
            os.kill(receipt["pid"], 0)
            receipt["alive_after_console_exit"] = True
        except ProcessLookupError:
            receipt["alive_after_console_exit"] = False
    if not receipt["success"]:
        # The caller deletes its simulator on failure; preserve the cause first.
        # CrashReporter can finish writing shortly after simctl returns.
        for _ in range(5):
            receipt["crash_diagnostics"] = crash_diagnostics(started_at, receipt.get("pid"), device)
            if receipt["crash_diagnostics"]:
                break
            time.sleep(1)
        if problem or receipt.get("alive_after_console_exit"):
            try:
                cleanup = subprocess.run(["xcrun", "simctl", "terminate", device, BUNDLE],
                                         capture_output=True, timeout=15)
                receipt["cleanup_returncode"] = cleanup.returncode
            except (OSError, subprocess.TimeoutExpired) as error:
                receipt["cleanup_error"] = type(error).__name__
    (output / "launch.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print(text)
    print(f"Keychain tests: simctl={code}, passed={len(receipt['passed'])}/{receipt['expected']}; "
          f"diagnostics: {output / 'launch.json'}")
    if not receipt["success"]:
        raise SystemExit(receipt["problem"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("device")
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    run(args.device, args.output)
