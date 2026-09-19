#!/usr/bin/env python3
"""Temporary hosted experiment; remove after choosing the CI implementation."""

import argparse
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--round", type=int, choices=range(3), required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    args = parser.parse_args()
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    spec = importlib.util.spec_from_file_location("phase", Path(__file__).with_name("run-ios-phase.py"))
    phase = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(phase)
    metadata = {"round": args.round}
    for label, command in {
        "identity_sha": ["git", "rev-parse", "HEAD"],
        "hardware": ["sysctl", "hw.memsize", "hw.logicalcpu", "hw.model"],
        "xcode": ["xcodebuild", "-version"],
        "java": ["java", "-version"],
    }.items():
        metadata[label] = subprocess.check_output(command, stderr=subprocess.STDOUT, text=True).strip()
    (output / "environment.json").write_text(json.dumps(metadata, indent=2) + "\n")
    module = ":waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:"
    assemble = module + "assembleWalletCoreReleaseXCFramework"
    profiles = Path("build/reports/profile")

    def measure(name, tasks, flags):
        # Each measurement has its own profile, command log and resource samples.
        if profiles.exists():
            shutil.rmtree(profiles)
        command = ["./gradlew", *tasks, "-PenableIosBuild=true", "--console=plain", "--profile", *flags]
        (output / f"{name}.command.json").write_text(json.dumps(command, indent=2) + "\n")
        code = phase.run_phase(name, command, output)
        if profiles.exists():
            shutil.copytree(profiles, output / f"{name}-profile", dirs_exist_ok=True)
        if code:
            raise SystemExit(code)
        if name != "warmup":
            log = (output / f"{name}.log").read_text()
            for target in ("IosArm64", "IosSimulatorArm64"):
                expected = "> Task " + module + "linkReleaseFramework" + target
                if expected not in log.splitlines():
                    raise RuntimeError(f"{name}: release link did not execute: {target}")

    measure("warmup", [assemble], ["--configuration-cache"])
    variants = {
        "default": ["--configuration-cache"],
        "one-worker": ["--configuration-cache", "--max-workers=1"],
        "two-workers": ["--configuration-cache", "--max-workers=2"],
        "no-config-cache": ["--no-configuration-cache"],
    }
    orders = [
        ["default", "one-worker", "two-workers", "no-config-cache"],
        ["one-worker", "no-config-cache", "default", "two-workers"],
        ["no-config-cache", "two-workers", "one-worker", "default"],
    ]
    for name in orders[args.round]:
        # Request only assemble, exactly as CI does. Listing the link tasks separately
        # can impose command-line ordering and spoil the concurrency comparison.
        build = Path("waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build")
        for target in ("iosArm64", "iosSimulatorArm64"):
            framework_dir = build / "bin" / target / "releaseFramework"
            if not (framework_dir / "WalletCore.framework").is_dir():
                raise RuntimeError(f"Missing warm framework: {framework_dir}")
            shutil.rmtree(framework_dir)
        shutil.rmtree(build / "XCFrameworks" / "release")
        measure(name, [assemble], variants[name])


if __name__ == "__main__":
    main()
