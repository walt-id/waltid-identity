#!/usr/bin/env python3
"""Check that an app and its extension load the same embedded Swift wallet runtime."""

import argparse
from pathlib import Path
import plistlib
import subprocess


def linked_wallet_frameworks(image):
    output = subprocess.check_output(["xcrun", "otool", "-L", str(image)], text=True)
    paths = (line.strip().split(" (", 1)[0] for line in output.splitlines()[1:])
    return {Path(path.removeprefix("@rpath/")) for path in paths if path.startswith("@rpath/WalletSDK")}


def wallet_runtime(bundle, frameworks):
    with (bundle / "Info.plist").open("rb") as info:
        executable = plistlib.load(info)["CFBundleExecutable"]
    # Xcode can put the executable's code and dependencies in a sibling dylib in Debug.
    images = [bundle / executable, *bundle.glob(f"{executable}.debug.dylib")]
    loaded = set()
    while images:
        for dependency in linked_wallet_frameworks(images.pop()) - loaded:
            image = frameworks / dependency
            if not image.is_file():
                raise SystemExit(f"{bundle.name} requires a missing embedded framework: {image}")
            loaded.add(dependency)
            images.append(image)
    return loaded


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("app", type=Path)
    parser.add_argument("extension", type=Path)
    args = parser.parse_args()
    frameworks = args.app / "Frameworks"
    if any((args.extension / "Frameworks").glob("WalletSDK*.framework")):
        raise SystemExit(f"{args.extension.name} embeds a second copy of a wallet framework")
    # Xcode may put the implementation in WalletSDK.framework when an adapter also uses it.
    runtime = Path("WalletSDK.framework/WalletSDK")
    if not (frameworks / runtime).is_file():
        runtime = Path("WalletSDKShared.framework/WalletSDKShared")
    for bundle in (args.app, args.extension):
        if runtime not in wallet_runtime(bundle, frameworks):
            raise SystemExit(f"{bundle.name} does not load the shared runtime {runtime}")
    print(f"{args.app.name} and {args.extension.name} load {runtime}")


if __name__ == "__main__":
    main()
