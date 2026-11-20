#!/usr/bin/env python3
"""Share one source-bound Xcode test-products build between the iOS CI suites."""

import argparse
import hashlib
import json
from pathlib import Path
import plistlib
import shutil
import stat
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parents[3]


def command(*args):
    return subprocess.check_output(args, text=True).strip()


def identity(demo):
    paths = {"identity": ROOT, "unified": ROOT.parent,
             "enterprise": ROOT.parent / "waltid-identity-enterprise",
             "enterprise_license": ROOT.parent / "waltid-identity-enterprise-license"}
    return {"demo": demo, "configuration": "Debug", "xcode": command("xcodebuild", "-version"),
            "revisions": {name: command("git", "-C", str(path), "rev-parse", "HEAD")
                          for name, path in paths.items()}}


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def inventory(products, demo):
    info = plistlib.loads((products / "Info.plist").read_bytes())
    tests = info["Tests"]
    if len(tests) != 1 or tests[0]["Architectures"] != ["arm64"] or tests[0]["Scheme"] != "iosApp":
        raise ValueError("Expected one arm64 iosApp test-products bundle")
    spec = products / tests[0]["xctestrun"]
    spec.resolve().relative_to(products.resolve())
    configurations = plistlib.loads(spec.read_bytes())["TestConfigurations"]
    expected = {"iosAppTests", "iosAppUITests"}
    if demo == "native":
        expected.add("iosAppEnterpriseTests")
    if (len(configurations) != 1 or not configurations[0]["IsEnabled"]
            or len(configurations[0]["TestTargets"]) != len(expected)
            or {t["BlueprintName"] for t in configurations[0]["TestTargets"]} != expected):
        raise ValueError(f"Expected all {demo} app test targets: {sorted(expected)}")
    files = {}
    for path in sorted(products.rglob("*")):
        name = str(path.relative_to(products))
        if path.is_symlink():
            path.resolve(strict=True).relative_to(products.resolve())
            files[name] = {"link": str(path.readlink())}
        elif path.is_file():
            files[name] = {"sha256": digest(path), "mode": stat.S_IMODE(path.stat().st_mode)}
    return files


def include_compose_framework(products):
    # Xcode includes Kotlin's build-product symlink alongside the embedded app framework.
    # Materialize that one dependency so no test product points back to the build runner.
    framework = products / "Binaries/0/Debug-iphonesimulator/sharedUI.framework"
    if framework.is_symlink():
        source = framework.resolve(strict=True)
        source.relative_to(ROOT / "waltid-applications/waltid-wallet-demo-compose/sharedUI/build/xcode-frameworks/Debug")
        framework.unlink()
        shutil.copytree(source, framework, symlinks=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=["pack", "restore", "verify"])
    parser.add_argument("--demo", choices=["native", "compose"], required=True)
    parser.add_argument("--artifact-dir", type=Path, required=True)
    args = parser.parse_args()
    directory = args.artifact_dir.resolve()
    products = directory / f"{args.demo}.xctestproducts"
    archive = directory / f"{args.demo}.tar.gz"
    manifest_path = directory / f"{args.demo}.json"
    expected = identity(args.demo)
    if args.operation == "pack":
        if args.demo == "compose":
            include_compose_framework(products)
        files = inventory(products, args.demo)
        with tarfile.open(archive, "w:gz", compresslevel=1) as stream:
            stream.add(products, arcname=products.name)
        manifest = {"schema": 1, **expected, "files": files, "archive_sha256": digest(archive)}
        manifest_path.write_text(json.dumps(manifest, indent=2) + "\n")
        print(f"Packed {args.demo} test products for {expected['revisions']['identity']}")
        return
    manifest = json.loads(manifest_path.read_text())
    if manifest.get("schema") != 1 or any(manifest.get(k) != v for k, v in expected.items()):
        raise ValueError("Test products must match the demo, configuration, Xcode and all source revisions")
    if args.operation == "restore":
        if digest(archive) != manifest["archive_sha256"]:
            raise ValueError("Test-products archive checksum mismatch")
        if products.exists():
            shutil.rmtree(products)
        with tarfile.open(archive) as stream:
            for member in stream.getmembers():
                path = Path(member.name)
                if path.is_absolute() or ".." in path.parts or path.parts[0] != products.name:
                    raise ValueError("Unexpected path in test-products archive")
            stream.extractall(directory, filter="data")
    if inventory(products, args.demo) != manifest["files"]:
        raise ValueError("Test products differ from the producing build")
    print(f"Verified {args.demo} test products for {expected['revisions']['identity']}")


if __name__ == "__main__":
    main()
