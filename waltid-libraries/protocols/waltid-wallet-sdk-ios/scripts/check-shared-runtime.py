#!/usr/bin/env python3
"""Check that an app and its extension load the same embedded Swift wallet runtime."""

import argparse
from pathlib import Path
import plistlib
import re
import subprocess


def linked_wallet_frameworks(image):
    output = subprocess.check_output(["xcrun", "otool", "-L", str(image)], text=True)
    paths = (line.strip().split(" (", 1)[0] for line in output.splitlines()[1:])
    return {Path(path.removeprefix("@rpath/")) for path in paths if path.startswith("@rpath/WalletSDK")}


def executable_images(bundle):
    with (bundle / "Info.plist").open("rb") as info:
        executable = plistlib.load(info)["CFBundleExecutable"]
    # Xcode can put the executable's code and dependencies in a sibling dylib in Debug.
    return [bundle / executable, *bundle.glob(f"{executable}.debug.dylib")]


def wallet_runtime(bundle, frameworks):
    images = executable_images(bundle)
    loaded = set()
    while images:
        for dependency in linked_wallet_frameworks(images.pop()) - loaded:
            image = frameworks / dependency
            if not image.is_file():
                raise SystemExit(f"{bundle.name} requires a missing embedded framework: {image}")
            loaded.add(dependency)
            images.append(image)
    return loaded


def defines_wallet_core(image):
    # Objective-C class definitions survive symbol stripping in a release archive.
    metadata = subprocess.check_output(["xcrun", "dyld_info", "-objc", str(image)], text=True)
    return re.search(r"^\s*@interface WalletCoreBase\s*:", metadata, re.MULTILINE) is not None


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
    runtime_image = frameworks / runtime
    consumer_images = set()
    for bundle in (args.app, args.extension):
        dependencies = wallet_runtime(bundle, frameworks)
        if runtime not in dependencies:
            raise SystemExit(f"{bundle.name} does not load the shared runtime {runtime}")
        consumer_images.update(executable_images(bundle))
        consumer_images.update(frameworks / path for path in dependencies)
    for test_bundle in (args.app / "PlugIns").glob("*.xctest"):
        consumer_images.update(executable_images(test_bundle))
    if not defines_wallet_core(runtime_image):
        raise SystemExit(f"{runtime_image} does not contain WalletCore")
    for image in sorted(consumer_images - {runtime_image}):
        if defines_wallet_core(image):
            raise SystemExit(f"{image} links a second copy of WalletCore alongside {runtime}")
    print(f"{args.app.name} and {args.extension.name} load {runtime}")


if __name__ == "__main__":
    main()
