#!/usr/bin/env python3
"""Capture phase logs and durations while propagating failures and cancellation."""

import argparse
import datetime
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import time


def utc_now():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def run_phase(phase, command, output_dir):
    output_dir.mkdir(parents=True, exist_ok=True)
    metadata_path = output_dir / f"{phase}.json"
    metadata = {"phase": phase, "started": utc_now(), "status": "running"}
    metadata_path.write_text(json.dumps(metadata, indent=2) + "\n")
    started = time.monotonic()
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
            cancel_deadline = time.monotonic() + 5
            terminate_group(signal.SIGTERM)

    previous_handlers = {sig: signal.signal(sig, cancel) for sig in (signal.SIGINT, signal.SIGTERM)}
    exit_code = 1
    try:
        print(f"iOS phase {phase}: started; output: {output_dir / (phase + '.log')}", flush=True)
        with (output_dir / f"{phase}.log").open("w") as log:
            process = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            while process.poll() is None:
                if cancel_deadline is not None and time.monotonic() >= cancel_deadline:
                    terminate_group(signal.SIGKILL)
                try:
                    process.wait(timeout=1)
                except subprocess.TimeoutExpired:
                    pass
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
