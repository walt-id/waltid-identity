#!/usr/bin/env python3
"""Resolve an explicit simulator destination against a simctl JSON inventory; never choose a device implicitly."""
import argparse
import json
import re
import sys


def select_simulator(destination, inventory):
    pairs = [part.strip().split("=", 1) for part in destination.split(",")]
    if any(len(pair) != 2 for pair in pairs) or len({pair[0] for pair in pairs}) != len(pairs):
        raise ValueError("Malformed or duplicate destination fields")
    fields = dict(pairs)
    if fields.pop("platform", None) != "iOS Simulator":
        raise ValueError("A simulator destination is required")
    if set(fields) - {"id", "name", "OS"} or bool(fields.get("id")) == bool(fields.get("name")):
        raise ValueError("Select exactly one simulator id or name")
    selected_os = fields.get("OS", "latest")
    candidates = []
    for runtime, devices in inventory["devices"].items():
        match = re.fullmatch(r"com\.apple\.CoreSimulator\.SimRuntime\.iOS-(\d+(?:-\d+)*)", runtime)
        if not match:
            continue
        version = tuple(map(int, match[1].split("-")))
        if selected_os != "latest" and version != tuple(map(int, selected_os.split("."))):
            continue
        for device in devices:
            if device.get("isAvailable") is not True:
                continue
            if fields.get("id", device["udid"]) != device["udid"] or fields.get("name", device["name"]) != device["name"]:
                continue
            candidates.append((version, device["udid"]))
    if not candidates:
        raise ValueError("No available simulator matches the destination")
    newest = max(version for version, _ in candidates)
    identifiers = [identifier for version, identifier in candidates if version == newest]
    if len(identifiers) != 1:
        raise ValueError("The destination is ambiguous; select its simulator id")
    return identifiers[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--destination", required=True)
    args = parser.parse_args()
    try:
        print(select_simulator(args.destination, json.load(sys.stdin)))
    except (ValueError, KeyError, TypeError) as error:
        parser.exit(1, f"Simulator selection rejected: {error}\n")


if __name__ == "__main__":
    main()
