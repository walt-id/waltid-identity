import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("run-ios-wallet-recovery-tests.sh")
DEVICE = "com.apple.CoreSimulator.SimDeviceType.iPhone-17"


class IosRecoveryDestinationTest(unittest.TestCase):
    def run_script(self, variables):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            script = root / ".github/scripts/mobile-ci" / SCRIPT.name
            script.parent.mkdir(parents=True)
            script.write_text(SCRIPT.read_text())
            commands = root / "commands"
            commands.mkdir()
            for name, body in {
                "python3": """
if sys.argv[1:2] == ['-c']:
    os.execv(sys.executable, [sys.executable, *sys.argv[1:]])
""",
                "xcrun": """
if sys.argv[1:4] == ['simctl', 'list', 'runtimes']:
    print(json.dumps({'runtimes': [
        {'name': 'iOS 27.0', 'version': '27.0', 'isAvailable': True,
         'identifier': 'com.apple.CoreSimulator.SimRuntime.iOS-27-0'},
        {'name': 'iOS 27.1', 'version': '27.1', 'isAvailable': True,
         'identifier': 'com.apple.CoreSimulator.SimRuntime.iOS-27-1'},
    ]}))
elif sys.argv[1:3] == ['simctl', 'create']:
    sys.exit(23)
else:
    sys.exit(91)
""",
            }.items():
                command = commands / name
                command.write_text(
                    f"#!{sys.executable}\nimport json, os, sys\n"
                    "with open(os.environ['RECOVERY_COMMAND_LOG'], 'a') as log:\n"
                    f"    log.write(json.dumps([{name!r}, *sys.argv[1:]]) + '\\n')\n"
                    + body
                )
                command.chmod(0o755)
            log = root / "commands.jsonl"
            env = {
                key: value for key, value in os.environ.items()
                if key not in ("IOS_SIMULATOR_DESTINATION", "IOS_RECOVERY_DEVICE_TYPE")
            }
            env.update(variables, PATH=str(commands) + os.pathsep + os.environ["PATH"],
                       RECOVERY_COMMAND_LOG=str(log))
            result = subprocess.run(["bash", str(script)], env=env, capture_output=True, text=True, timeout=10)
            calls = [json.loads(line) for line in log.read_text().splitlines()] if log.exists() else []
            return result, calls

    def test_destination_and_device_override_reach_simulator_creation(self):
        cases = [
            ({}, [DEVICE]),
            ({"IOS_RECOVERY_DEVICE_TYPE": "iPhone 18 Pro"}, ["iPhone 18 Pro"]),
            ({"IOS_SIMULATOR_DESTINATION": "platform=iOS Simulator,name=iPhone 17,OS=27.0"},
             ["iPhone 17", "iOS 27.0"]),
            ({"IOS_SIMULATOR_DESTINATION": "platform=iOS Simulator,name=iPhone 18 Pro,OS=27.0"},
             ["iPhone 18 Pro", "iOS 27.0"]),
            ({"IOS_SIMULATOR_DESTINATION": "platform=iOS Simulator,name=iPhone 17,OS=27.0.1"},
             ["iPhone 17", "iOS 27.0.1"]),
            ({"IOS_SIMULATOR_DESTINATION": "platform=iOS Simulator,name=iPhone 17,OS=27.0",
              "IOS_RECOVERY_DEVICE_TYPE": "iPhone 18 Pro"}, ["iPhone 18 Pro", "iOS 27.0"]),
        ]
        for variables, expected in cases:
            with self.subTest(variables=variables):
                result, calls = self.run_script(variables)
                self.assertEqual(result.returncode, 23, result.stderr)
                creates = [call for call in calls if call[:3] == ["xcrun", "simctl", "create"]]
                self.assertEqual(creates, [["xcrun", "simctl", "create", "Wallet recovery", *expected]])
                self.assertEqual(calls[-1], creates[0])

    def test_invalid_explicit_destination_fails_before_external_work(self):
        for destination in (
            "", "platform=macOS,name=iPhone 17,OS=27.0",
            "platform=iOS Simulator,id=existing-device,OS=27.0",
            "platform=iOS Simulator,OS=27.0",
            "platform=iOS Simulator,name=,OS=27.0",
            "platform=iOS Simulator,name=iPhone 17,OS=latest",
            "platform=iOS Simulator,name=iPhone 17,OS=27",
            "platform=iOS Simulator,name=iPhone 17,OS=27.x",
            "platform=iOS Simulator,name=iPhone 17,OS=27.0\nOS=27.1",
            "platform=iOS Simulator,name=iPhone 17,OS=27.0,",
            "platform=iOS Simulator,name=iPhone 17,OS=27.0,extra=value",
        ):
            with self.subTest(destination=destination):
                result, calls = self.run_script({"IOS_SIMULATOR_DESTINATION": destination})
                self.assertEqual(result.returncode, 1, result.stderr)
                self.assertIn("device-type name and explicit iOS version", result.stderr)
                self.assertEqual(calls, [])


if __name__ == "__main__":
    unittest.main()
