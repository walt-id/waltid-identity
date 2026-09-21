#!/usr/bin/env python3
"""Pack and verify the exact release WalletCore used by the iOS CI consumers."""

import argparse
import hashlib
import json
from pathlib import Path
import plistlib
import shutil
import subprocess
import tarfile

FRAMEWORK = Path("waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/XCFrameworks/release/WalletCore.xcframework")
MANIFEST = "wallet-core.json"
ARCHIVE = "wallet-core.tar.gz"


def command(*args):
    return subprocess.check_output(args, text=True).strip()


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def inventory(framework):
    with (framework / "Info.plist").open("rb") as stream:
        libraries = plistlib.load(stream)["AvailableLibraries"]
    platforms = {(entry["SupportedPlatform"], entry.get("SupportedPlatformVariant", ""),
                  tuple(entry["SupportedArchitectures"])) for entry in libraries}
    if platforms != {("ios", "", ("arm64",)), ("ios", "simulator", ("arm64",))} or len(libraries) != 2:
        raise ValueError("WalletCore must contain both device and simulator arm64 release slices")
    for entry in libraries:
        binary = framework / entry["LibraryIdentifier"] / entry["LibraryPath"] / "WalletCore"
        if not binary.is_file() or binary.stat().st_size == 0:
            raise ValueError(f"Missing framework binary: {binary}")
    return {str(path.relative_to(framework)): digest(path)
            for path in sorted(framework.rglob("*")) if path.is_file()}


def identity():
    return {"identity_sha": command("git", "rev-parse", "HEAD"),
            "xcode": command("xcodebuild", "-version"), "configuration": "release"}


def read_manifest(artifact_dir):
    manifest = json.loads((artifact_dir / MANIFEST).read_text())
    if manifest.get("schema") != 1:
        raise ValueError("Unsupported WalletCore artifact manifest")
    for key, expected in identity().items():
        if manifest.get(key) != expected:
            raise ValueError(f"WalletCore {key} mismatch: expected {expected!r}, got {manifest.get(key)!r}")
    return manifest


def verify(artifact_dir):
    manifest = read_manifest(artifact_dir)
    if inventory(FRAMEWORK) != manifest["files"]:
        raise ValueError("WalletCore contents differ from the producing build")
    print(f"Verified full release WalletCore for {manifest['identity_sha']}", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=["pack", "restore", "verify"])
    parser.add_argument("--artifact-dir", required=True, type=Path)
    args = parser.parse_args()
    directory = args.artifact_dir.resolve()
    archive = directory / ARCHIVE
    if args.operation == "pack":
        files = inventory(FRAMEWORK)
        directory.mkdir(parents=True, exist_ok=True)
        with tarfile.open(archive, "w:gz") as stream:
            stream.add(FRAMEWORK, arcname=FRAMEWORK.name)
        manifest = {"schema": 1, **identity(), "files": files, "archive_sha256": digest(archive)}
        (directory / MANIFEST).write_text(json.dumps(manifest, indent=2) + "\n")
        print(f"Packed full release WalletCore for {manifest['identity_sha']}", flush=True)
    elif args.operation == "restore":
        manifest = read_manifest(directory)
        if digest(archive) != manifest["archive_sha256"]:
            raise ValueError("WalletCore archive checksum mismatch")
        if FRAMEWORK.exists():
            shutil.rmtree(FRAMEWORK)
        FRAMEWORK.parent.mkdir(parents=True, exist_ok=True)
        with tarfile.open(archive) as stream:
            for member in stream.getmembers():
                path = Path(member.name)
                if path.is_absolute() or ".." in path.parts or path.parts[0] != FRAMEWORK.name:
                    raise ValueError("Unexpected path in WalletCore archive")
            stream.extractall(FRAMEWORK.parent, filter="data")
        verify(directory)
    else:
        verify(directory)


if __name__ == "__main__":
    main()
