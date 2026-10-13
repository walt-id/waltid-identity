"""Check simulator isolation and failure propagation without touching real simulators."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

RUNNER = Path(__file__).with_name("run-ios-app-tests.sh")

class IosAppRunnerTest(unittest.TestCase):
    def run_app(self, test_status=0, cleanup_status=0, destination="platform=iOS Simulator,name=iPhone 17,OS=27.0"):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            log = root / "calls.jsonl"
            for tool in ("xcrun", "xcodebuild"):
                executable = root / tool
                executable.write_text("#!/usr/bin/env python3\n" + r'''import json, os, pathlib, sys
with pathlib.Path(os.environ["CALL_LOG"]).open("a") as stream:
    stream.write(json.dumps([pathlib.Path(sys.argv[0]).name, *sys.argv[1:]]) + "\n")
if sys.argv[1:3] == ["simctl", "create"]:
    print("test-owned-uuid")
if sys.argv[1:3] == ["simctl", "delete"]:
    sys.exit(int(os.environ["CLEANUP_STATUS"]))
if pathlib.Path(sys.argv[0]).name == "xcodebuild":
    sys.exit(int(os.environ["TEST_STATUS"]))
''')
                executable.chmod(0o755)
            env = {**os.environ, "PATH": str(root) + os.pathsep + os.environ["PATH"],
                   "CALL_LOG": str(log), "TEST_STATUS": str(test_status), "CLEANUP_STATUS": str(cleanup_status)}
            result = subprocess.run(["bash", str(RUNNER), destination, "-only-testing:iosAppUITests/BatchIssuanceE2ETests"],
                                    env=env, capture_output=True, text=True, timeout=10)
            calls = [json.loads(line) for line in log.read_text().splitlines()] if log.exists() else []
            return result, calls

    def test_uses_and_removes_only_its_own_simulator(self):
        result, calls = self.run_app()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(calls[-1], ["xcrun", "simctl", "delete", "test-owned-uuid"])
        test_call = next(call for call in calls if call[0] == "xcodebuild")
        self.assertIn("platform=iOS Simulator,id=test-owned-uuid", test_call)
        self.assertIn("-only-testing:iosAppUITests/BatchIssuanceE2ETests", test_call)
        self.assertIn("Debug", test_call)

    def test_preserves_test_failure_even_when_cleanup_fails(self):
        result, calls = self.run_app(test_status=65, cleanup_status=1)
        self.assertEqual(result.returncode, 65)
        self.assertEqual(calls[-1][1:3], ["simctl", "delete"])

    def test_cleanup_failure_cannot_report_success(self):
        result, _ = self.run_app(cleanup_status=1)
        self.assertEqual(result.returncode, 1)

    def test_rejects_ambiguous_destination_before_creating_simulator(self):
        result, calls = self.run_app(destination="platform=iOS Simulator,name=iPhone 17")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(calls, [])

if __name__ == "__main__":
    unittest.main()
