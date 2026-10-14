"""Exercise orchestration boundaries without starting Gradle or a simulator."""
import contextlib
import io
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import verify


class ComparisonRunnerTests(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.workspace = Path(directory.name).resolve()
        self.root = self.workspace / "waltid-identity"
        self.root.mkdir()
        self.output = self.root / "report"
        self.commands = []

    def execute(self, renderers, *, failure=None, reuse=False, coordinated=False):
        arguments = ["verify.py", "--renderers", *renderers, "--output", str(self.output)]
        if any(renderer != "android" for renderer in renderers):
            arguments += ["--simulator", "owned-simulator"]
        if reuse:
            arguments += ["--reuse-framework", str(self.root / "artifact")]
        if coordinated:
            arguments += ["--gradle-root", str(self.workspace)]

        def run(command, **options):
            self.commands.append((command, options))
            if "begin" in command:
                (self.output / "run.json").write_text(json.dumps({
                    "runId": "fresh-run", "sha": "a" * 40, "fingerprint": "b" * 64}))
            return SimpleNamespace(returncode=1 if failure in command else 0)

        with patch.object(verify, "ROOT", self.root), patch("sys.argv", arguments), \
             patch.object(verify, "check_apple_environment") as apple, \
             patch.object(verify.subprocess, "run", side_effect=run), \
             patch.object(verify.time, "monotonic", side_effect=range(20)), \
             contextlib.redirect_stdout(io.StringIO()):
            status = verify.main()
        return status, apple

    def test_android_runs_the_complete_class_without_apple_or_framework_work(self):
        status, apple = self.execute(["android"])
        self.assertEqual(status, 0)
        apple.assert_not_called()
        commands = [command for command, _ in self.commands]
        gradle = next(command for command in commands if command[0].endswith("gradlew"))
        self.assertIn("--rerun", gradle)
        self.assertIn("*WalletVisualAndroidTest", gradle)
        self.assertIn("-PenableIosBuild=false", gradle)
        self.assertIn("-Proborazzi.test.verify=true", gradle)
        self.assertFalse(any("assembleWalletCoreReleaseXCFramework" in arg for command in commands for arg in command))
        timings = json.loads((self.output / "timings.json").read_text())
        self.assertEqual([(item["phase"], item["seconds"], item["exitCode"]) for item in timings],
                         [("compose", 1, 0), ("report", 1, 0)])

    def test_native_reuses_the_verified_artifact_and_forwards_verification_identity(self):
        status, apple = self.execute(["swiftui"], reuse=True, coordinated=True)
        self.assertEqual(status, 0)
        apple.assert_called_once_with("owned-simulator", native=True)
        commands = [command for command, _ in self.commands]
        self.assertIn("verify", commands[0])
        self.assertIn("--artifact-dir", commands[0])
        self.assertEqual(self.commands[0][1]["cwd"], self.root)
        self.assertFalse(any(command[0].endswith("gradlew") for command in commands))
        native = next(command for command in commands if command[0] == "xcodebuildmcp")
        self.assertIn("-only-testing:iosAppTests/WalletVisualTests", native)
        self.assertIn("-only-testing:iosAppTests/WalletVisualComparatorTests", native)
        self.assertIn("-only-testing:iosAppTests/WalletVisualHostingTests", native)
        self.assertEqual(native[native.index("-parallel-testing-enabled") + 1], "NO")
        environment = json.loads(native[native.index("--json") + 1])["testRunnerEnv"]
        self.assertEqual(environment["WALLET_VISUAL_RUN_ID"], "fresh-run")
        self.assertEqual(environment["WALLET_VISUAL_RECORD"], "0")

    def test_invalid_reused_framework_stops_without_comparing_or_building_a_replacement(self):
        status, _ = self.execute(["swiftui"], reuse=True, failure="verify")
        self.assertEqual(status, 1)
        self.assertEqual(len(self.commands), 1)
        self.assertFalse((self.output / "run.json").exists())
        self.assertEqual(json.loads((self.output / "timings.json").read_text())[0]["exitCode"], 1)

    def test_missing_report_evidence_fails_even_after_a_successful_test_process(self):
        status, _ = self.execute(["android"], failure="finish")
        self.assertEqual(status, 1)

    def test_compose_ios_compares_the_complete_class_without_a_release_framework(self):
        status, apple = self.execute(["compose-ios"])
        self.assertEqual(status, 0)
        apple.assert_called_once_with("owned-simulator", native=False)
        gradle = next(command for command, _ in self.commands if command[0].endswith("gradlew"))
        self.assertIn("*WalletVisualIosTest", gradle)
        self.assertIn("owned-simulator", gradle)
        self.assertFalse(any(command[0] == "xcodebuildmcp" for command, _ in self.commands))


class AppleEnvironmentTests(unittest.TestCase):
    def output(self, *command, device_type=verify.NATIVE_DEVICE):
        if command[0] == "xcodebuild":
            return "Xcode 27.0\nBuild version 27A266a"
        if command[0] == "xcodebuildmcp":
            return "2.7.0"
        return json.dumps({"devices": {verify.RUNTIME: [{
            "udid": "owned-simulator", "deviceTypeIdentifier": device_type}]}})

    def test_compose_needs_only_xcode_and_the_pinned_runtime(self):
        output = lambda *command: self.output(*command, device_type="com.apple.CoreSimulator.SimDeviceType.iPhone-13")
        with patch.object(verify, "checked_output", side_effect=output) as command:
            verify.check_apple_environment("owned-simulator", native=False)
        self.assertFalse(any(call.args[0] == "xcodebuildmcp" for call in command.call_args_list))

    def test_native_accepts_the_pinned_runtime_and_hardware(self):
        with patch.object(verify, "checked_output", side_effect=self.output):
            verify.check_apple_environment("owned-simulator", native=True)

    def test_native_rejects_other_or_unknown_hardware_on_the_same_runtime(self):
        for device_type in ("com.apple.CoreSimulator.SimDeviceType.iPhone-13", None):
            with self.subTest(device_type=device_type):
                output = lambda *command: self.output(*command, device_type=device_type)
                with patch.object(verify, "checked_output", side_effect=output), \
                     self.assertRaisesRegex(SystemExit, "iPhone 17"):
                    verify.check_apple_environment("owned-simulator", native=True)

    def test_a_different_runtime_is_rejected(self):
        with patch.object(verify, "checked_output", side_effect=self.output), \
             self.assertRaisesRegex(SystemExit, "iOS 27.0"):
            verify.check_apple_environment("other-runtime-simulator", native=True)


if __name__ == "__main__":
    unittest.main()
