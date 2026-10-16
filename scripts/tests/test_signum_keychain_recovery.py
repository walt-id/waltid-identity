import importlib.util
import json
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

SCRIPT = (Path(__file__).resolve().parents[2] / "waltid-libraries/crypto/waltid-crypto2-signum/scripts/run_keychain_tests.py")
spec = importlib.util.spec_from_file_location("signum_keychain", SCRIPT)
runner = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runner)

ONE = "[==========] Running 1 tests from 1 test cases.\n[ RUN      ] ownership.reopen\n"
PASSED = ONE + "[       OK ] ownership.reopen (12 ms)\n[  PASSED  ] 1 tests.\n"


class KeychainReportTest(unittest.TestCase):
    def setUp(self):
        temporary = TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.output = Path(temporary.name)

    def test_complete_suite_preserves_real_test_results(self):
        result = runner.report(self.output, PASSED, 0)
        self.assertTrue(result["success"])
        xml = ET.parse(self.output / "results.xml").getroot()
        self.assertEqual({"name": "signum.keychain", "tests": "1", "errors": "0", "failures": "0"}, xml.attrib)
        self.assertEqual("0.012", xml[0].get("time"))

    def test_interrupted_test_is_reported_as_no_result(self):
        result = runner.report(self.output, ONE, -9)
        self.assertFalse(result["success"])
        self.assertEqual(["ownership.reopen"], result["unfinished"])
        xml = ET.parse(self.output / "results.xml").getroot()
        self.assertEqual("1", xml.get("errors"))
        self.assertIsNotNone(xml[0].find("error"))

    def test_missing_summary_cannot_pass_even_if_every_started_test_passed(self):
        result = runner.report(self.output, PASSED.replace("[  PASSED  ] 1 tests.\n", ""), 0)
        self.assertFalse(result["success"])
        self.assertEqual("host-completion", ET.parse(self.output / "results.xml").getroot()[-1].get("name"))

    def test_undiscovered_or_duplicate_cases_cannot_pass(self):
        for text in (PASSED.replace("Running 1", "Running 2"), PASSED + PASSED,
                     "[==========] Running 0 tests from 0 test cases.\n[  PASSED  ] 0 tests.\n", ""):
            with self.subTest(text=text):
                self.assertFalse(runner.report(self.output, text, 0)["success"])

    def test_nonzero_exit_or_skips_cannot_pass_a_complete_summary(self):
        for text, code in ((PASSED, 1), (PASSED + "[  SKIPPED ] unexpected\n", 0),
                           (PASSED + "[  FAILED  ] unexpected\n", 0)):
            with self.subTest(code=code, text=text):
                self.assertFalse(runner.report(self.output, text, code)["success"])

    def test_assertion_is_distinct_from_a_disappearing_process(self):
        result = runner.report(self.output, ONE + "[  FAILED  ] ownership.reopen (12 ms)\n", 1)
        self.assertFalse(result["success"])
        self.assertEqual([], result["unfinished"])
        xml = ET.parse(self.output / "results.xml").getroot()
        self.assertEqual("1", xml.get("failures"))
        self.assertEqual("0", xml.get("errors"))

    def test_incomplete_console_exit_keeps_status_and_publishes_failure_junit(self):
        def launch(*args, **kwargs):
            kwargs["stdout"].write(ONE)
            return subprocess.CompletedProcess(args[0], 137)

        with patch.object(runner.subprocess, "run", side_effect=launch), \
                patch.object(runner, "crash_diagnostics", return_value=[]), patch.object(runner.time, "sleep"), \
                patch("builtins.print"):
            with self.assertRaises(SystemExit):
                runner.run("specific-device", self.output)
        receipt = json.loads((self.output / "launch.json").read_text())
        self.assertEqual(137, receipt["simctl_returncode"])
        self.assertEqual(["ownership.reopen"], receipt["unfinished"])
        self.assertEqual("1", ET.parse(self.output / "results.xml").getroot().get("errors"))

    def test_timeout_keeps_partial_results_and_bounds_cleanup(self):
        def command(arguments, **kwargs):
            if "launch" in arguments:
                kwargs["stdout"].write(ONE)
                raise subprocess.TimeoutExpired(arguments, 120)
            self.assertIn("terminate", arguments)
            self.assertEqual(15, kwargs["timeout"])
            raise subprocess.TimeoutExpired(arguments, 15)

        with patch.object(runner.subprocess, "run", side_effect=command), \
                patch.object(runner, "crash_diagnostics", return_value=[]), patch.object(runner.time, "sleep"), \
                patch("builtins.print"):
            with self.assertRaisesRegex(SystemExit, "exceeded 120"):
                runner.run("specific-device", self.output)
        receipt = json.loads((self.output / "launch.json").read_text())
        self.assertIsNone(receipt["simctl_returncode"])
        self.assertEqual("TimeoutExpired", receipt["cleanup_error"])
        self.assertTrue((self.output / "results.xml").exists())

    def test_crash_capture_keeps_termination_metadata_without_raw_private_values(self):
        reports = self.output / "Library/Logs/DiagnosticReports"
        reports.mkdir(parents=True)
        (reports / "KeychainTests-current.ips").write_text(json.dumps({"app_name": "KeychainTests"}) + "\n" +
            json.dumps({"pid": 123, "exception": {"type": "EXC_CRASH", "signal": "SIGKILL"},
                        "termination": {"namespace": "RUNNINGBOARD", "code": 1234, "private": "DO_NOT_PUBLISH"},
                        "faultingThread": 0, "threads": [{"frames": [{"symbol": "CFRelease", "imageIndex": 0}]}],
                        "usedImages": [{"name": "CoreFoundation", "private": "DO_NOT_PUBLISH"}],
                        "environment": {"secret": "DO_NOT_PUBLISH"}, "memory": "DO_NOT_PUBLISH"}))
        with patch.object(runner.Path, "home", return_value=self.output):
            records = runner.crash_diagnostics(0)
            self.assertEqual([], runner.crash_diagnostics(10**12))
            self.assertEqual([], runner.crash_diagnostics(0, pid=124))
        self.assertEqual({"namespace": "RUNNINGBOARD", "code": 1234}, records[0]["termination"])
        self.assertEqual([{"symbol": "CFRelease", "image": "CoreFoundation"}], records[0]["faulting_stack"])
        self.assertNotIn("DO_NOT_PUBLISH", json.dumps(records))

    def test_jetsam_capture_keeps_only_the_terminated_test_process(self):
        reports = self.output / "Library/Logs/DiagnosticReports"
        reports.mkdir(parents=True)
        (reports / "JetsamEvent-current.ips").write_text(json.dumps({"pageSize": 16384, "processes": [
            {"name": "KeychainTests", "pid": 123, "reason": "per-process-limit", "rpages": 456},
            {"name": "unrelated", "pid": 456, "reason": "secret", "private": "DO_NOT_PUBLISH"}]}))
        with patch.object(runner.Path, "home", return_value=self.output):
            records = runner.crash_diagnostics(0, pid=123)
            self.assertEqual([], runner.crash_diagnostics(0, pid=124))
        self.assertEqual("per-process-limit", records[0]["jetsam"]["reason"])
        self.assertNotIn("DO_NOT_PUBLISH", json.dumps(records))


if __name__ == "__main__":
    unittest.main()
