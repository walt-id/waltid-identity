"""Exercise the real XCTest launcher with a stub xcodebuild, without Apple hardware."""
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


LAUNCHER = Path(__file__).with_name("run-xcode-tests.sh")


class XcodeDiagnosticsTest(unittest.TestCase):
    def run_launcher(self, mode=None, exit_code=0):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            stub = root / "xcodebuild"
            stub.write_text(f"#!{sys.executable}\n" +
                            "import json, os, sys\nprint(json.dumps(sys.argv[1:]))\n"
                            "sys.exit(int(os.environ['STUB_EXIT']))\n")
            stub.chmod(0o755)
            env = {**os.environ, "PATH": str(root) + os.pathsep + os.environ["PATH"],
                   "STUB_EXIT": str(exit_code)}
            env.pop("XCODE_TEST_DIAGNOSTICS", None)
            if mode is not None:
                env["XCODE_TEST_DIAGNOSTICS"] = mode
            return subprocess.run(
                ["bash", str(LAUNCHER), "-scheme", "Wallet tests", "-resultBundlePath", "results.xcresult"],
                env=env, capture_output=True, text=True, timeout=10)

    def test_default_and_opt_in_preserve_test_arguments(self):
        for mode, expected in [(None, "never"), ("never", "never"), ("on-failure", "on-failure")]:
            with self.subTest(mode=mode):
                result = self.run_launcher(mode)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(json.loads(result.stdout), [
                    "test", "-collect-test-diagnostics", expected, "-scheme", "Wallet tests",
                    "-resultBundlePath", "results.xcresult"])

    def test_xcode_failure_is_not_hidden_in_either_mode(self):
        for mode in ("never", "on-failure"):
            with self.subTest(mode=mode):
                self.assertEqual(self.run_launcher(mode, exit_code=65).returncode, 65)

    def test_invalid_mode_does_not_launch_xcode(self):
        result = self.run_launcher("always")
        self.assertEqual(result.returncode, 2)
        self.assertEqual(result.stdout, "")
        self.assertIn("must be never or on-failure", result.stderr)


if __name__ == "__main__":
    unittest.main()
