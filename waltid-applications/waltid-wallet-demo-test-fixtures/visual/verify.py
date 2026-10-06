#!/usr/bin/env python3
"""Compare the wallet catalogue locally or in CI. Never record or accept baselines."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
UI = ":waltid-applications:waltid-wallet-demo-compose:sharedUI"
RUNTIME = "com.apple.CoreSimulator.SimRuntime.iOS-26-5"


def checked_output(*command):
    return subprocess.check_output(command, cwd=ROOT, text=True).strip()


def check_apple_environment(simulator):
    if checked_output("xcodebuild", "-version") != "Xcode 27.0\nBuild version 27A266a":
        raise SystemExit("Wallet baselines require Xcode 27.0 (27A266a). Review an environment update explicitly.")
    if checked_output("xcodebuildmcp", "--version") != "2.7.0":
        raise SystemExit("Use XcodeBuildMCP 2.7.0 for the catalogue's native result schema.")
    devices = json.loads(checked_output("xcrun", "simctl", "list", "devices", "available", "--json"))
    if not any(device["udid"] == simulator for device in devices["devices"].get(RUNTIME, [])):
        raise SystemExit("Choose an available, test-owned iOS 26.5 simulator with --simulator. No latest-runtime fallback is allowed.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--simulator", help="Test-owned iOS 26.5 simulator UUID; required for either iOS renderer")
    parser.add_argument("--renderers", nargs="+", choices=["android", "compose-ios", "swiftui"],
                        default=["android", "compose-ios", "swiftui"])
    parser.add_argument("--output", type=Path, default=ROOT / "build/reports/wallet-visual")
    parser.add_argument("--derived-data", type=Path, default=ROOT / "build/wallet-visual-derived")
    parser.add_argument("--gradle-root", type=Path, default=ROOT,
                        help="Identity checkout or unified build whose waltid-identity resolves to this checkout")
    args = parser.parse_args()
    args.gradle_root = args.gradle_root.resolve()
    if args.gradle_root != ROOT and (args.gradle_root / "waltid-identity").resolve() != ROOT:
        parser.error("--gradle-root must build this Identity checkout")
    args.output = args.output.resolve()
    args.output.mkdir(parents=True, exist_ok=True)
    uses_ios = bool(set(args.renderers) & {"compose-ios", "swiftui"})
    if uses_ios:
        if not args.simulator:
            parser.error("--simulator is required for iOS renderers")
        check_apple_environment(args.simulator)
    env = dict(os.environ, TZ="UTC")
    gradle = [str(args.gradle_root / "gradlew"), "--console=plain", "--max-workers=2",
              f"-PenableAndroidBuild={'true' if 'android' in args.renderers else 'false'}",
              f"-PenableIosBuild={'true' if uses_ios else 'false'}",
              "-Proborazzi.test.record=false", "-Proborazzi.test.verify=true"]

    def run(command, log):
        print(f"Running {log}; output: {args.output / log}", flush=True)
        with (args.output / log).open("w") as output:
            return subprocess.run(command, cwd=args.gradle_root, env=env, stdout=output, stderr=subprocess.STDOUT).returncode

    # Build before freezing source/baseline fingerprints. This also prepares the Swift consumer.
    if "swiftui" in args.renderers:
        status = run(gradle + [":waltid-libraries:protocols:waltid-openid4vc-wallet-mobile:assembleWalletCoreReleaseXCFramework"], "framework.log")
        if status:
            return status
    report = [sys.executable, str(HERE / "report.py")]
    subprocess.run(report + ["begin", "--output", str(args.output), "--renderers", *args.renderers], cwd=ROOT, check=True)
    tasks = []
    if "android" in args.renderers:
        tasks += [f"{UI}:testAndroidHostTest", "--rerun", "--tests", "*WalletVisualAndroidTest"]
    if "compose-ios" in args.renderers:
        tasks += [f"{UI}:iosSimulatorArm64Test", "--rerun", "--device", args.simulator, "--tests", "*WalletVisualIosTest"]
    codes = [run(gradle + tasks, "compose.log")] if tasks else []
    native = args.output / "native-results.json"
    if "swiftui" in args.renderers:
        # Explicitly forward CI detection so a child XCTest runner cannot accept references.
        test_env = {"E2E_USE_MOCK_WALLET": "1", "WALLET_VISUAL_RECORD": "0", "TZ": "UTC"}
        if any(key in env for key in ("CI", "GITHUB_ACTIONS", "GITLAB_CI", "BUILD_BUILDID", "JENKINS_URL", "TEAMCITY_VERSION")):
            test_env["CI"] = "true"
        command = ["xcodebuildmcp", "simulator", "test", "--project-path",
                   str(ROOT / "waltid-applications/waltid-wallet-demo-ios/iosApp/iosApp.xcodeproj"),
                   "--scheme", "iosApp", "--configuration", "Debug", "--simulator-id", args.simulator,
                   "--derived-data-path", str(args.derived_data.resolve()), "--extra-args",
                   "-only-testing:iosAppTests/WalletVisualTests", "CODE_SIGNING_ALLOWED=YES", "CODE_SIGN_IDENTITY=-",
                   "--json", json.dumps({"testRunnerEnv": test_env}), "--output", "json"]
        with native.open("w") as output, (args.output / "native-stderr.log").open("w") as errors:
            codes.append(subprocess.run(command, cwd=ROOT, env=env, stdout=output, stderr=errors).returncode)
    finish = report + ["finish", "--output", str(args.output)]
    if "swiftui" in args.renderers:
        finish += ["--native-results", str(native)]
    codes.append(subprocess.run(finish, cwd=ROOT).returncode)
    return 1 if any(codes) else 0


if __name__ == "__main__":
    raise SystemExit(main())
