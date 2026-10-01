"""Resolve the Enterprise test simulator on a runtime supported by the selected Xcode."""
import json
import subprocess
import sys


def version_tuple(version):
    parts = tuple(map(int, version.split(".")))
    return parts + (0,) * (3 - len(parts))


def resolve_simulator(inventory, sdk_version):
    sdk = version_tuple(sdk_version)
    runtimes = [runtime for runtime in inventory["runtimes"]
                if runtime["isAvailable"] and runtime["name"].startswith("iOS ")
                and version_tuple(runtime["version"]) <= sdk]
    if not runtimes:
        raise RuntimeError(f"No available iOS simulator runtime compatible with SDK {sdk_version}")
    runtime = max(runtimes, key=lambda item: version_tuple(item["version"]))
    devices = [device for device in inventory["devices"].get(runtime["identifier"], [])
               if device["isAvailable"] and device["name"] == "iPhone 17"]
    return runtime, devices[0]["udid"] if devices else None


def main():
    inventory = json.loads(subprocess.check_output(["xcrun", "simctl", "list", "--json"], text=True))
    sdk = subprocess.check_output(["xcrun", "--sdk", "iphonesimulator", "--show-sdk-version"], text=True).strip()
    runtime, simulator = resolve_simulator(inventory, sdk)
    if simulator is None:
        simulator = subprocess.check_output([
            "xcrun", "simctl", "create", "iPhone 17",
            "com.apple.CoreSimulator.SimDeviceType.iPhone-17", runtime["identifier"],
        ], text=True).strip()
    print(f"Enterprise simulator: {runtime['name']}, {simulator}", file=sys.stderr)
    print(f"platform=iOS Simulator,id={simulator}")


if __name__ == "__main__":
    main()
