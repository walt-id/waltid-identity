#!/usr/bin/env python3
"""Retain iOS phase output and resource samples, including when CI cancels a phase."""

import argparse
import datetime
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import threading
import time


def utc_now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def sample_resources(output, stop, interval):
    # Use executable names, not full command lines or environment values: JVM arguments
    # and fixture command lines can contain credentials.
    commands = [
        ["ps", "-axo", "pid,ppid,%cpu,rss,comm"],
        ["vm_stat"],
        ["sysctl", "vm.swapusage"],
        ["df", "-k", str(output.parent)],
    ]
    with output.open("w") as samples:
        while not stop.is_set():
            snapshot = {"time": utc_now()}
            for command in commands:
                if stop.is_set():
                    break
                try:
                    result = subprocess.run(command, capture_output=True, text=True, timeout=5)
                    snapshot[command[0]] = result.stdout if result.returncode == 0 else result.stderr
                except (OSError, subprocess.TimeoutExpired) as error:
                    snapshot[command[0]] = type(error).__name__
            samples.write(json.dumps(snapshot) + "\n")
            samples.flush()
            stop.wait(interval)


def run_phase(phase, command, output_dir, interval=30):
    output_dir.mkdir(parents=True, exist_ok=True)
    metadata_path = output_dir / f"{phase}.json"
    metadata = {"phase": phase, "started": utc_now(), "status": "running"}
    metadata_path.write_text(json.dumps(metadata, indent=2) + "\n")
    started = time.monotonic()
    stop = threading.Event()
    monitor = threading.Thread(
        target=sample_resources, args=(output_dir / f"{phase}.resources.jsonl", stop, interval), daemon=True
    )
    monitor.start()
    received_signal = None
    cancel_deadline = None
    process = None

    def terminate_group(sig):
        if process is not None:
            try:
                os.killpg(process.pid, sig)
            except ProcessLookupError:
                pass

    def cancel(signum, _frame):
        nonlocal received_signal, cancel_deadline
        if received_signal is None:
            received_signal = signum
            cancel_deadline = time.monotonic() + 10
            terminate_group(signal.SIGTERM)

    previous_handlers = {sig: signal.signal(sig, cancel) for sig in (signal.SIGINT, signal.SIGTERM)}
    exit_code = 1
    try:
        print(f"iOS phase {phase}: started; output: {output_dir / (phase + '.log')}", flush=True)
        with (output_dir / f"{phase}.log").open("w") as log:
            process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            next_heartbeat = started + interval
            while process.poll() is None:
                if cancel_deadline is not None and time.monotonic() >= cancel_deadline:
                    terminate_group(signal.SIGKILL)
                try:
                    process.wait(timeout=1)
                except subprocess.TimeoutExpired:
                    pass
                if time.monotonic() >= next_heartbeat:
                    print(f"iOS phase {phase}: {time.monotonic() - started:.0f}s elapsed", flush=True)
                    next_heartbeat = time.monotonic() + interval
            exit_code = process.returncode
            if exit_code < 0:
                exit_code = 128 - exit_code
    except OSError as error:
        print(f"iOS phase {phase}: could not start command: {error}", flush=True)
    finally:
        if received_signal is not None:
            # Also clean up descendants if the direct child exited before its children.
            terminate_group(signal.SIGKILL)
            exit_code = 128 + received_signal
        stop.set()
        monitor.join(timeout=6)
        for sig, handler in previous_handlers.items():
            signal.signal(sig, handler)
        metadata.update(
            finished=utc_now(), elapsed_seconds=round(time.monotonic() - started, 3),
            exit_code=exit_code, status="cancelled" if received_signal else "completed",
        )
        metadata_path.write_text(json.dumps(metadata, indent=2) + "\n")
        print(f"iOS phase {phase}: exit {exit_code}, {metadata['elapsed_seconds']}s", flush=True)
    return exit_code


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("phase")
    parser.add_argument("command", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    if not re.fullmatch(r"[a-z0-9][a-z0-9-]*", args.phase):
        parser.error("phase must be a lowercase filename component")
    command = args.command[1:] if args.command[:1] == ["--"] else args.command
    if not command:
        parser.error("a command is required after the phase")
    return run_phase(args.phase, command, args.output_dir)


if __name__ == "__main__":
    raise SystemExit(main())
