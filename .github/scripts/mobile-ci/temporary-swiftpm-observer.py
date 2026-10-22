#!/usr/bin/env python3
"""Temporary same-command framework observation; never part of the durable harness."""

import datetime
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import time


def now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def capture(directory, name, command, timeout=15):
    started = time.monotonic()
    with (directory / (name + ".log")).open("w") as output:
        process = subprocess.Popen(command, stdout=output, stderr=subprocess.STDOUT)
        try:
            code = process.wait(timeout=timeout)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
            code = 124
    return {"command": command, "exit_code": code, "seconds": round(time.monotonic() - started, 3)}


def snapshot(output, label, pids, transport=False):
    directory = output / label
    directory.mkdir()
    records = {"started": now()}
    commands = {
        "processes": ["ps", "-U", str(os.getuid()), "-o", "pid,ppid,pgid,etime,pcpu,pmem,rss,comm"],
        "memory": ["vm_stat"],
        "swap": ["sysctl", "vm.swapusage", "vm.loadavg"],
        "disk": ["df", "-k", os.getcwd()],
    }
    for name, command in commands.items():
        records[name] = capture(directory, name, command)
    for pid in pids[:3]:
        records[f"sample-{pid}"] = capture(directory, f"sample-command-{pid}",
            ["sample", str(pid), "5", "-mayDie", "-file", str(directory / f"sample-{pid}.txt")], 20)
        records[f"files-{pid}"] = capture(directory, f"files-{pid}", ["lsof", "-nP", "-p", str(pid)])
    if transport:
        payload = output.parent / "swiftpm-independent-download.zip"
        try:
            records["transport"] = capture(directory, "transport", [
                "curl", "--location", "--fail", "--silent", "--show-error",
                "--connect-timeout", "15", "--max-time", "90", "--output", str(payload),
                "--write-out", "http=%{http_code} bytes=%{size_download} connect=%{time_connect} start=%{time_starttransfer} total=%{time_total}\n",
                "https://github.com/sqlcipher/SQLCipher.swift/releases/download/4.19.0/SQLCipher.xcframework.zip",
            ], 95)
            if payload.exists():
                records["download"] = {"bytes": payload.stat().st_size, "sha256": hashlib.sha256(payload.read_bytes()).hexdigest()}
        finally:
            payload.unlink(missing_ok=True)
    records["finished"] = now()
    (directory / "capture.json").write_text(json.dumps(records, indent=2) + "\n")


def observe(output):
    began = time.monotonic()
    first_seen, captures = {}, set()
    fallback_captured = False
    transport_captured = False
    last_log_size = -1
    while True:
        result = subprocess.run(["ps", "-U", str(os.getuid()), "-o", "pid=,comm="],
                                capture_output=True, text=True, timeout=10)
        swift, java = [], []
        for line in result.stdout.splitlines():
            fields = line.strip().split(None, 1)
            if len(fields) != 2:
                continue
            pid, command = int(fields[0]), Path(fields[1]).name
            if command in {"swift-package", "swift"}:
                swift.append(pid)
                first_seen.setdefault(pid, time.monotonic())
            elif command == "java":
                java.append(pid)
        phase_log = output / "wallet-core-framework.log"
        size = phase_log.stat().st_size if phase_log.exists() else 0
        if size != last_log_size:
            with (output / "observations.jsonl").open("a") as stream:
                stream.write(json.dumps({"time": now(), "phase_log_bytes": size, "swift_pids": swift, "java_pids": java}) + "\n")
            last_log_size = size
        for pid in swift:
            for age in (120, 600, 1800):
                if (pid, age) not in captures and time.monotonic() - first_seen[pid] >= age:
                    captures.add((pid, age))
                    snapshot(output, f"swift-{pid}-{age}s", [pid], not transport_captured)
                    transport_captured = True
        if not swift and not fallback_captured and time.monotonic() - began >= 600:
            fallback_captured = True
            snapshot(output, "no-resolver-at-10m", java)
        time.sleep(5)


def main():
    os.umask(0o077)
    output = Path(sys.argv[2]).resolve()
    output.mkdir(parents=True, exist_ok=True)
    if sys.argv[1] == "observe":
        observe(output)
        return
    with (output / "observer.log").open("w") as log:
        observer = subprocess.Popen([sys.executable, __file__, "observe", str(output)],
                                    stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            spec = importlib.util.spec_from_file_location("original_phase", ".github/scripts/mobile-ci/run-ios-phase.py")
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            return module.run_phase("wallet-core-framework", sys.argv[3:], output)
        finally:
            try:
                os.killpg(observer.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
            try:
                observer.wait(timeout=5)
            except subprocess.TimeoutExpired:
                os.killpg(observer.pid, signal.SIGKILL)
                observer.wait(timeout=5)
            (output.parent / "swiftpm-independent-download.zip").unlink(missing_ok=True)


if __name__ == "__main__":
    sys.exit(main())
