#!/usr/bin/env python3
"""Check that an app and its extension load the same embedded Swift wallet runtime."""

import argparse
from pathlib import Path
import plistlib
import re
import subprocess


def image_linkage(image, bundle):
    output = subprocess.check_output(["xcrun", "otool", "-l", str(image)], text=True)
    commands = re.findall(
        r"^\s*cmd (LC_[A-Z_]+)\n\s*cmdsize \d+\n\s*(?:name|path) (.+?) \(offset \d+\)$",
        output, re.MULTILINE,
    )
    anchors = {"@executable_path": bundle, "@loader_path": image.parent}
    paths, dependencies = [], []
    for command, value in commands:
        anchor, _, suffix = value.partition("/")
        if command == "LC_RPATH" and anchor in anchors:
            paths.append((anchors[anchor] / suffix).resolve())
        elif command != "LC_ID_DYLIB" and anchor == "@rpath" and command.endswith("_DYLIB"):
            dependencies.append(Path(suffix))
    return paths, dependencies


def bundle_executable(bundle):
    with (bundle / "Info.plist").open("rb") as info:
        return bundle / plistlib.load(info)["CFBundleExecutable"]


def loaded_images(bundle, frameworks):
    executable = bundle_executable(bundle)
    pending = [(executable, [])]
    visited = set()
    while pending:
        image, inherited = pending.pop()
        if image in visited:
            continue
        visited.add(image)
        paths, dependencies = image_linkage(image, bundle)
        # An embedded image inherits the runpaths of the images that loaded it.
        paths.extend(inherited)
        for relative in dependencies:
            expected = frameworks / relative
            if relative == Path(f"{executable.name}.debug.dylib"):
                expected = bundle / relative
            elif not expected.is_file() and not relative.parts[0].startswith("WalletSDK"):
                continue
            resolved = next((path / relative for path in paths if (path / relative).is_file()), None)
            if resolved != expected:
                raise SystemExit(f"{bundle.name}: {image.name} cannot resolve @rpath/{relative} to {expected}")
            pending.append((resolved, paths))
    return visited


def defines_wallet_core(image):
    # Objective-C class definitions survive symbol stripping in a release archive.
    metadata = subprocess.check_output(["xcrun", "dyld_info", "-objc", str(image)], text=True)
    return re.search(r"^\s*@interface WalletCoreBase\s*:", metadata, re.MULTILINE) is not None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("app", type=Path)
    parser.add_argument("extension", type=Path)
    args = parser.parse_args()
    app = args.app.resolve()
    extension = args.extension.resolve()
    frameworks = app / "Frameworks"
    if app not in extension.parents:
        raise SystemExit(f"{extension} is outside {app}")
    if any((extension / "Frameworks").glob("WalletSDK*.framework")):
        raise SystemExit(f"{extension.name} embeds a second copy of a wallet framework")
    app_images = loaded_images(app, frameworks)
    extension_images = loaded_images(extension, frameworks)
    consumer_images = app_images | extension_images
    consumer_images.update(bundle_executable(framework) for framework in frameworks.glob("WalletSDK*.framework"))
    for test_bundle in (app / "PlugIns").glob("*.xctest"):
        test = bundle_executable(test_bundle)
        consumer_images.add(test)
        consumer_images.update(test_bundle.glob(f"{test.name}.debug.dylib"))
    owners = sorted(image for image in consumer_images if defines_wallet_core(image))
    if len(owners) != 1:
        names = ", ".join(str(image.relative_to(app)) for image in owners) or "none"
        raise SystemExit(f"Expected one image defining WalletCore in {app.name}, found {names}")
    runtime = owners[0]
    if runtime.parent.parent != frameworks or runtime not in app_images & extension_images:
        raise SystemExit(f"{app.name} and {extension.name} must both load {runtime} from {frameworks}")
    print(f"{app.name} and {extension.name} load {runtime.relative_to(frameworks)}")


if __name__ == "__main__":
    main()
