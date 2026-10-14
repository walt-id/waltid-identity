#!/usr/bin/env python3
"""Compare the wallet catalogue locally or in CI. Never record or accept baselines."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time

import report

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
UI = ":waltid-applications:waltid-wallet-demo-compose:sharedUI"
RUNTIME = "com.apple.CoreSimulator.SimRuntime.iOS-27-0"
NATIVE_DEVICE = "com.apple.CoreSimulator.SimDeviceType.iPhone-17"


def checked_output(*command):
    return subprocess.check_output(command, cwd=ROOT, text=True).strip()


def check_apple_environment(simulator, native):
    if checked_output("xcodebuild", "-version") != "Xcode 27.0\nBuild version 27A266a":
        raise SystemExit("Wallet baselines require Xcode 27.0 (27A266a). Review an environment update explicitly.")
    if native and checked_output("xcodebuildmcp", "--version") != "2.7.0":
        raise SystemExit("Use XcodeBuildMCP 2.7.0 for the catalogue's native result schema.")
    devices = json.loads(checked_output("xcrun", "simctl", "list", "devices", "available", "--json"))
    device = next((device for device in devices["devices"].get(RUNTIME, []) if device["udid"] == simulator), None)
    if device is None:
        raise SystemExit("Choose an available, test-owned iOS 27.0 simulator with --simulator. No latest-runtime fallback is allowed.")
    if native and device.get("deviceTypeIdentifier") != NATIVE_DEVICE:
        raise SystemExit("SwiftUI baselines require an iPhone 17 simulator on iOS 27.0; other hardware changes hosted safe areas.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--simulator", help="Test-owned iOS 27.0 simulator UUID; SwiftUI requires iPhone 17 hardware")
    parser.add_argument("--renderers", nargs="+", choices=["android", "compose-ios", "swiftui"],
                        default=["android", "compose-ios", "swiftui"])
    parser.add_argument("--output", type=Path, default=ROOT / "build/reports/wallet-visual")
    parser.add_argument("--derived-data", type=Path, default=ROOT / "build/wallet-visual-derived")
    parser.add_argument("--reuse-framework", type=Path,
                        help="Verified WalletCore artifact from the same checkout and Xcode; SwiftUI only")
    parser.add_argument("--gradle-root", type=Path, default=ROOT,
                        help="Identity checkout or unified build whose waltid-identity resolves to this checkout")
    args = parser.parse_args()
    if args.reuse_framework and "swiftui" not in args.renderers:
        parser.error("--reuse-framework requires the swiftui renderer")
    args.gradle_root = args.gradle_root.resolve()
    if args.gradle_root != ROOT and (args.gradle_root / "waltid-identity").resolve() != ROOT:
        parser.error("--gradle-root must build this Identity checkout")
    args.output = args.output.resolve()
    args.output.mkdir(parents=True, exist_ok=True)
    uses_ios = bool(set(args.renderers) & {"compose-ios", "swiftui"})
    if uses_ios:
        if not args.simulator:
            parser.error("--simulator is required for iOS renderers")
        check_apple_environment(args.simulator, native="swiftui" in args.renderers)
    env = dict(os.environ, TZ="UTC")
    gradle = [str(args.gradle_root / "gradlew"), "--console=plain", "--max-workers=2",
              f"-PenableAndroidBuild={'true' if 'android' in args.renderers else 'false'}",
              f"-PenableIosBuild={'true' if uses_ios else 'false'}",
              "-Proborazzi.test.record=false", "-Proborazzi.test.verify=true"]

    timings = []

    def run(command, log, stderr=None, cwd=None):
        print(f"Running {log}; output: {args.output / log}", flush=True)
        started = time.monotonic()
        with (args.output / log).open("w") as output:
            status = subprocess.run(command, cwd=cwd or args.gradle_root, env=env, stdout=output,
                                    stderr=stderr if stderr is not None else subprocess.STDOUT).returncode
        timings.append({"phase": log.removesuffix(".log").removesuffix(".json"),
                        "seconds": round(time.monotonic() - started, 3), "exitCode": status})
        (args.output / "timings.json").write_text(json.dumps(timings, indent=2) + "\n")
        print(f"{timings[-1]['phase']}: {timings[-1]['seconds']}s (exit {status})", flush=True)
        return status

    # Build before freezing source/baseline fingerprints. This also prepares the Swift consumer.
    if "swiftui" in args.renderers:
        framework_command = ([sys.executable, str(ROOT / ".github/scripts/mobile-ci/wallet-core-artifact.py"),
                              "verify", "--artifact-dir", str(args.reuse_framework.resolve())]
                             if args.reuse_framework else gradle + [
                                 ":waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:assembleWalletCoreReleaseXCFramework"])
        status = run(framework_command, "framework.log", cwd=ROOT if args.reuse_framework else args.gradle_root)
        if status:
            return status
    report_command = [sys.executable, str(HERE / "report.py")]
    subprocess.run(report_command + ["begin", "--output", str(args.output), "--gradle-root", str(args.gradle_root),
                                    "--renderers", *args.renderers], cwd=ROOT, check=True)
    run_metadata = json.loads((args.output / "run.json").read_text())
    env.update(report.runner_env(run_metadata))
    env.update({"SIMCTL_CHILD_" + key: value for key, value in report.runner_env(run_metadata).items()})
    tasks = []
    if "android" in args.renderers:
        tasks += [f"{UI}:testAndroidHostTest", "--rerun", "--tests", "*WalletVisualAndroidTest"]
    if "compose-ios" in args.renderers:
        tasks += [f"{UI}:iosSimulatorArm64Test", "--rerun", "--device", args.simulator, "--tests", "*WalletVisualIosTest"]
    codes = [run(gradle + tasks, "compose.log")] if tasks else []
    native = args.output / "native-results.json"
    if "swiftui" in args.renderers:
        # Explicitly forward CI detection so a child XCTest runner cannot accept references.
        test_env = report.runner_env(run_metadata)
        command = ["xcodebuildmcp", "simulator", "test", "--project-path",
                   str(ROOT / "waltid-applications/waltid-wallet-demo-ios/iosApp/iosApp.xcodeproj"),
                   "--scheme", "iosApp", "--configuration", "Debug", "--simulator-id", args.simulator,
                   "--derived-data-path", str(args.derived_data.resolve()), "--extra-args",
                   "-only-testing:iosAppTests/WalletVisualTests", "-only-testing:iosAppTests/WalletVisualComparatorTests",
                   "-only-testing:iosAppTests/WalletVisualHostingTests",
                   "-parallel-testing-enabled", "NO",
                   "CODE_SIGNING_ALLOWED=YES", "CODE_SIGN_IDENTITY=-",
                   "--json", json.dumps({"testRunnerEnv": test_env}), "--output", "json"]
        with (args.output / "native-stderr.log").open("w") as errors:
            codes.append(run(command, "native-results.json", stderr=errors))
    finish = report_command + ["finish", "--output", str(args.output)]
    if "swiftui" in args.renderers:
        finish += ["--native-results", str(native)]
    codes.append(run(finish, "report.log"))
    return 1 if any(codes) else 0


if __name__ == "__main__":
    raise SystemExit(main())
