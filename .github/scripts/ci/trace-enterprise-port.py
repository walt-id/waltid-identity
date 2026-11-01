"""Temporary WAL-1585 observer: socket metadata and JVM stacks, never payloads/env."""
import json
import os
from pathlib import Path
import re
import subprocess
import time

root = Path(os.environ["RUNNER_TEMP"]) / "enterprise-startup-diagnostic"
root.mkdir(exist_ok=True)
build_log = Path(os.environ["RUNNER_TEMP"]) / "gradle-build.log"
deadline = time.monotonic() + 72 * 60
last = None
failed_at = None
captures = set()
log_offset = 0
tail = ""


def workers():
    found = {}
    for proc in Path("/proc").glob("[0-9]*"):
        try:
            command = (proc / "cmdline").read_bytes().replace(b"\0", b" ")
            match = re.search(rb"Gradle Test Executor (\d+)", command)
            if match:
                found[int(proc.name)] = {
                    "executor": int(match[1]),
                    "cwd": str((proc / "cwd").resolve()),
                }
        except (OSError, ProcessLookupError):
            pass
    return found


with (root / "sockets.jsonl").open("w", buffering=1) as out:
    out.write(json.dumps({"ephemeral_range": Path("/proc/sys/net/ipv4/ip_local_port_range").read_text().strip()}) + "\n")
    while time.monotonic() < deadline:
        sockets = []
        for family in ("tcp", "tcp6"):
            for row in Path("/proc/net/" + family).read_text().splitlines()[1:]:
                fields = row.split()
                if int(fields[1].split(":")[1], 16) == 33334:
                    sockets.append({"family": family, "local": fields[1], "remote": fields[2], "state": fields[3], "inode": fields[9]})
        snapshot = json.dumps(sockets, sort_keys=True)
        if snapshot != last:
            inodes = {s["inode"] for s in sockets}
            owners = []
            for proc in Path("/proc").glob("[0-9]*"):
                try:
                    for fd in (proc / "fd").iterdir():
                        link = os.readlink(fd)
                        if any(link == "socket:[" + inode + "]" for inode in inodes):
                            owners.append({"pid": int(proc.name), "comm": (proc / "comm").read_text().strip(), "cwd": str((proc / "cwd").resolve()), "socket": link})
                except (OSError, ProcessLookupError):
                    pass
            event = {"time": time.time(), "sockets": sockets, "owners": owners, "test_workers": workers()}
            out.write(json.dumps(event) + "\n")
            print("PORT_DIAGNOSTIC " + json.dumps(event), flush=True)
            last = snapshot
        if build_log.exists():
            with build_log.open(errors="replace") as log:
                log.seek(log_offset)
                chunk = log.read()
                log_offset = log.tell()
            tail = (tail + chunk)[-262144:]
            if failed_at is None and "BindException" in tail:
                failed_at = time.monotonic()
        if failed_at is not None:
            for after in (0, 60, 300):
                if after not in captures and time.monotonic() - failed_at >= after:
                    captures.add(after)
                    for pid, worker in workers().items():
                        target = root / f"threads-{after}-{pid}.txt"
                        with target.open("w") as stack:
                            stack.write(json.dumps(worker) + "\n")
                            try:
                                subprocess.run(["jcmd", str(pid), "Thread.print"], stdout=stack, stderr=subprocess.STDOUT, timeout=15, check=False)
                            except subprocess.TimeoutExpired:
                                stack.write("Thread capture exceeded 15 seconds.\n")
        time.sleep(0.1)
