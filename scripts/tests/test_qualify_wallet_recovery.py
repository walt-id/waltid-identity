import importlib.util
import json
import plistlib
import sys
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch
from types import SimpleNamespace
import xml.etree.ElementTree as ET

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
spec = importlib.util.spec_from_file_location("qualification", Path(__file__).resolve().parents[1] / "qualify-wallet-recovery.py")
qualification = importlib.util.module_from_spec(spec)
spec.loader.exec_module(qualification)


class QualificationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.run = qualification.Qualification.__new__(qualification.Qualification)
        self.run.directory = Path(self.temporary.name)
        self.run.state_file = self.run.directory / "checkpoint.json"
        self.run.state = {"phase": "prepare", "events": [], "runId": "test", "storage": "EncryptedDatabase", "restoreStorage": "EncryptedDatabase"}

    def test_skipped_or_empty_suite_is_not_recovery_evidence(self):
        for output in ("OK (0 tests)", "INSTRUMENTATION_FAILED: Process crashed", "FAILURES!!!\nOK (1 test)",
                       "INSTRUMENTATION_STATUS_CODE: -3\nOK (1 test)\n"):
            self.run.shell = lambda *args: output
            with self.assertRaises(RuntimeError):
                self.run.phase("verify")
            self.assertEqual("prepare", self.run.state["phase"])
            self.assertEqual("failed", self.run.state["events"][-1]["outcome"])

    def test_success_is_persisted_for_a_later_invocation(self):
        self.run.shell = lambda *args: "\nOK (1 test)\n"
        self.run.phase("verify")
        self.assertEqual("verify", json.loads(self.run.state_file.read_text())["phase"])

    def test_restore_requires_evidence_of_local_loss(self):
        self.run.shell = lambda *args: self.fail("Device must not be contacted")
        with self.assertRaises(ValueError):
            self.run.phase("restore")

    def test_reinstall_requires_a_prepared_wallet(self):
        self.run.state["phase"] = "new"
        with self.assertRaises(ValueError):
            self.run.reinstall()

    def test_reinstall_retry_only_installs_after_successful_uninstall(self):
        self.run.state["phase"] = "reinstall"
        installed = []
        self.run.install = lambda: installed.append(True)
        self.run.reinstall()
        self.assertEqual([True], installed)

    def test_prepare_requires_public_checkpoint_even_if_test_runner_succeeded(self):
        self.run.state["phase"] = "new"
        self.run.shell = lambda *args: "\nOK (1 test)\n"
        with self.assertRaises(RuntimeError):
            self.run.phase("prepare")
        self.assertEqual("new", self.run.state["phase"])
        self.assertEqual("failed", self.run.state["events"][-1]["outcome"])

    def test_junit_reports_a_failed_phase_and_successful_cleanup(self):
        self.run.shell = lambda *args: "INSTRUMENTATION_FAILED: crashed"
        with self.assertRaises(RuntimeError):
            self.run.phase("verify")
        self.run.shell = lambda *args: "OK (1 test)\n"
        self.run.phase("cleanup")
        suite = ET.parse(self.run.directory / "results.xml").getroot()
        self.assertEqual("2", suite.get("tests"))
        self.assertEqual("1", suite.get("failures"))
        self.assertIsNotNone(suite.find("testcase/failure"))

    def test_sdk_location_is_explicit(self):
        args = SimpleNamespace(output=self.run.directory, platform="android", device="emulator-id")
        with patch.dict(qualification.os.environ, {}, clear=True):
            with self.assertRaisesRegex(ValueError, "ANDROID_HOME"):
                qualification.Qualification(args)

    def test_ios_uses_completion_transport_and_selected_simulator(self):
        self.run.args = SimpleNamespace(device="simulator-id")
        with patch.object(qualification, "run_test", return_value="test output") as run_test:
            self.assertEqual("test output", self.run.ios_phase("verify"))
        device, arguments = run_test.call_args.args
        self.assertEqual("simulator-id", device)
        self.assertIn("--ktest_filter=" + qualification.IOS_TEST, arguments)
        self.assertIn("--recoveryPhase=verify", arguments)

    def test_invalid_public_checkpoint_is_reported_as_a_failure(self):
        self.run.state["phase"] = "new"
        self.run.shell = lambda *args: "recoveryCheckpoint=e30=\nOK (1 test)\n"
        with self.assertRaises(RuntimeError):
            self.run.phase("prepare")
        self.assertEqual("new", self.run.state["phase"])
        self.assertEqual("1", ET.parse(self.run.directory / "results.xml").getroot().get("failures"))

    def test_ambiguous_simulator_selection_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "exact device"):
            qualification.Qualification(SimpleNamespace(device="booted"))

    def test_resume_rejects_a_changed_source_or_destination(self):
        app = self.run.directory / "RecoveryTests.app"
        app.mkdir()
        (app / "Info.plist").write_bytes(plistlib.dumps({"CFBundleIdentifier": qualification.IOS_PACKAGE}))
        args = SimpleNamespace(device="simulator-id", output=self.run.directory, platform="ios-simulator",
                               app=app, storage="EncryptedDatabase", restore_storage="NativeStorage", command="prepare")
        qualification.Qualification(args)
        qualification.Qualification(args)  # An unchanged configuration remains resumable.
        for changes in ({"storage": "NativeStorage"}, {"restore_storage": "EncryptedDatabase"}):
            changed = SimpleNamespace(**(vars(args) | changes))
            with self.assertRaisesRegex(ValueError, "storage configuration"):
                qualification.Qualification(changed)

    def test_cross_storage_verification_switches_only_after_successful_restore(self):
        self.run.state["restoreStorage"] = "NativeStorage"
        self.assertEqual("EncryptedDatabase", self.run.storage_for("verify"))
        self.assertEqual("NativeStorage", self.run.storage_for("restore"))
        self.run.state["events"].append({"phase": "restore", "outcome": "failed"})
        self.assertEqual("EncryptedDatabase", self.run.storage_for("verify"))
        self.run.state["events"].append({"phase": "restore", "outcome": "passed"})
        self.assertEqual("NativeStorage", self.run.storage_for("verify"))

    def test_ios_reinstall_is_rejected(self):
        self.run.state["platform"] = "ios-simulator"
        with self.assertRaises(ValueError):
            self.run.reinstall()

    def test_transport_failure_preserves_phase_and_records_failure(self):
        def fail(*args):
            raise RuntimeError("Device disconnected")
        self.run.shell = fail
        with self.assertRaises(RuntimeError):
            self.run.phase("verify")
        checkpoint = json.loads(self.run.state_file.read_text())
        self.assertEqual("prepare", checkpoint["phase"])
        self.assertEqual("failed", checkpoint["events"][-1]["outcome"])


if __name__ == "__main__":
    unittest.main()
