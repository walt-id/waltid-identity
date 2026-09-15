import sys
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import ios_simulator_test as host


class SimulatorHostTest(unittest.TestCase):
    def setUp(self):
        directory = TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.container = Path(directory.name)
        (self.container / "Documents").mkdir()
        self.run = "24000119-bb78-4601-a5b2-51988fa88fa2"
        self.log = self.container / "Documents" / (self.run + ".log")
        self.calls = []
        self.output = None

        def simctl(*arguments, env=None):
            self.calls.append((arguments, env))
            if arguments[0] == "get_app_container":
                return str(self.container) + "\n"
            if arguments[0] == "launch":
                if self.output is not None:
                    self.log.write_text(self.output)
                return host.PACKAGE + ": 71536\n"
            return ""

        self.addCleanup(patch.stopall)
        patch.object(host, "simctl", side_effect=simctl).start()
        patch.object(host.uuid, "uuid4", return_value=self.run).start()

    def test_completed_host_returns_output_from_the_selected_simulator(self):
        self.output = "[  PASSED  ] 1 tests.\nRECOVERY_HOST_EXIT=0\n"
        self.assertEqual(self.output, host.run_test("simulator-id", ["--ktest_filter=example"]))
        arguments, env = self.calls[1]
        self.assertEqual(("launch", "--terminate-running-process", "simulator-id", host.PACKAGE,
                          "--ktest_filter=example"), arguments)
        self.assertEqual(self.run, env["SIMCTL_CHILD_RECOVERY_HOST_RUN"])

    def test_pid_alone_and_stale_log_cannot_pass(self):
        (self.container / "Documents/previous-run.log").write_text("[  PASSED  ] 1 tests.\nRECOVERY_HOST_EXIT=0\n")
        with self.assertRaisesRegex(RuntimeError, "did not report completion"):
            host.run_test("simulator-id", [], timeout=0)
        self.assertEqual(("terminate", "simulator-id", host.PACKAGE), self.calls[-1][0])

    def test_partial_passing_output_requires_completion(self):
        self.output = "[  PASSED  ] 1 tests.\n"
        with self.assertRaisesRegex(RuntimeError, r"PASSED[\s\S]+did not report completion"):
            host.run_test("simulator-id", [], timeout=0)

    def test_nonzero_completion_preserves_diagnostics(self):
        self.output = "assertion failed\nRECOVERY_HOST_EXIT=1\n"
        with self.assertRaisesRegex(RuntimeError, "assertion failed"):
            host.run_test("simulator-id", [])

    def test_empty_or_failed_kotlin_suite_cannot_pass(self):
        for output in ("RECOVERY_HOST_EXIT=0\n", "[  PASSED  ] 0 tests.\n",
                       "[  PASSED  ] 1 tests.\n[  FAILED  ] 1 tests.\n"):
            self.assertFalse(host.passed_one_test(output))

    def test_transport_failure_writes_log_and_junit(self):
        with patch.object(host, "run_test", side_effect=RuntimeError("partial log\nmissing completion")), \
                patch.object(sys, "argv", ["ios_simulator_test.py", "--device", "simulator-id",
                                           "--output", str(self.container), "--", "--ktest_filter=example"]):
            with self.assertRaises(SystemExit):
                host.main()
        self.assertIn("partial log", (self.container / "keychain-contract.log").read_text())
        self.assertEqual("1", ET.parse(self.container / "results.xml").getroot().get("failures"))


if __name__ == "__main__":
    unittest.main()
