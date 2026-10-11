"""Negative contracts: a passing XCTest alone must never certify a visual comparison."""
import json
from pathlib import Path
import tempfile
import unittest

from PIL import Image, PngImagePlugin
import report


class NativeReceiptTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.baseline, self.actual = self.root / "baseline.png", self.root / "actual.png"
        for path in (self.baseline, self.actual):
            Image.new("RGB", (2, 3), "red").save(path)
        self.state = {"id": "test.screen", "swiftTest": "testScreen"}
        self.case = {"status": "passed"}
        self.run = {"runId": "current-run", "sha": "a" * 40, "fingerprint": "b" * 64}
        self.result = {**self.run, "schemaVersion": 1, "id": "test.screen", "renderer": "swiftui",
            "kind": "deterministic", "test": "testScreen", "mode": "verify", "compared": True, "result": "passed",
            "baselineDigest": report.digest(self.baseline), "actualDigest": report.digest(self.actual),
            "actual_file_path": str(self.actual), "viewport": [2, 3], "scale": 1, "timezone": "UTC"}

    def status(self, **changes):
        return report.assess_native(self.state, self.case, {**self.result, **changes}, self.baseline, self.run, {})[0]

    def test_valid_comparison_is_accepted(self):
        self.assertEqual(self.status(), "passed")

    def test_passing_xctest_without_receipt_is_missing(self):
        self.assertEqual(report.assess_native(self.state, self.case, {}, self.baseline, self.run, {})[0], "missing")

    def test_receipt_without_test_result_is_missing(self):
        self.case = {}
        self.assertEqual(self.status(), "missing")

    def test_each_source_and_run_binding_is_required(self):
        for key in self.run:
            with self.subTest(key=key):
                self.assertEqual(self.status(**{key: "old"}), "stale")

    def test_wrong_cell_renderer_evidence_kind_mode_or_test_is_invalid(self):
        for key in ("id", "renderer", "kind", "mode", "test", "schemaVersion"):
            with self.subTest(key=key):
                self.assertEqual(self.status(**{key: "different"}), "invalid")

    def test_recording_never_passes(self):
        self.assertEqual(self.status(mode="record"), "recorded")
        self.assertEqual(self.status(result="recorded"), "recorded")

    def test_changed_baseline_is_stale(self):
        Image.new("RGB", (2, 3), "blue").save(self.baseline)
        self.assertEqual(self.status(), "stale")

    def test_missing_baseline_is_explicit(self):
        self.baseline.unlink()
        self.assertEqual(self.status(), "missing-baseline")

    def test_missing_malformed_or_wrong_actual_cannot_pass(self):
        for payload in (None, b"not a PNG", b"\x89PNG\r\n\x1a\n"):
            with self.subTest(payload=payload):
                if payload is None:
                    self.actual.unlink(missing_ok=True)
                else:
                    self.actual.write_bytes(payload)
                self.assertEqual(self.status(), "invalid")

    def test_wrong_actual_digest_is_invalid(self):
        self.assertEqual(self.status(actualDigest="c" * 64), "invalid")

    def test_wrong_viewport_density_or_timezone_is_invalid(self):
        for change in ({"viewport": [3, 2]}, {"scale": 2}, {"timezone": "Europe/Vienna"}):
            with self.subTest(change=change):
                self.assertEqual(self.status(**change), "invalid")

    def test_resolved_configuration_must_match_capture(self):
        result = report.assess_native(self.state, self.case, self.result, self.baseline, self.run, {"theme": "dark"})
        self.assertEqual(result[0], "invalid")

    def test_comparison_must_have_executed(self):
        for value in (False, None, "true", 1):
            with self.subTest(value=value):
                self.assertEqual(self.status(compared=value), "failed")

    def test_test_and_comparison_failures_remain_failures(self):
        self.assertEqual(self.status(result="failed"), "failed")
        self.case = {"status": "failed"}
        self.assertEqual(self.status(), "failed")

    def test_skipped_test_remains_skipped(self):
        self.case = {"status": "skipped"}
        self.assertEqual(self.status(), "skipped")

    def test_reencoding_does_not_change_pixel_identity(self):
        metadata = PngImagePlugin.PngInfo()
        metadata.add_text("comment", "Encoding-only update")
        Image.new("RGB", (2, 3), "red").save(self.actual, pnginfo=metadata, compress_level=0)
        self.assertNotEqual(report.image_info(self.baseline)["digest"], report.image_info(self.actual)["digest"])
        self.assertEqual(report.image_info(self.baseline)["pixels"], report.image_info(self.actual)["pixels"])

    def export(self, attachments):
        (self.root / "manifest.json").write_text(json.dumps([{"testIdentifier": "WalletVisualTests/testScreen()", "attachments": attachments}]))
        return report.native_attachments(self.root, {"testScreen": self.case})

    def test_export_associates_capture_with_receipt_digest(self):
        receipt = self.root / "receipt.json"
        receipt.write_text(json.dumps(self.result))
        results = self.export([
            {"exportedFileName": receipt.name, "suggestedHumanReadableName": "wallet-visual-receipt-test-screen_0_UUID.json"},
            {"exportedFileName": self.actual.name, "suggestedHumanReadableName": "wallet-visual-actual-test-screen_0_UUID.png"}])
        self.assertEqual(results["test.screen"]["actual_file_path"], str(self.actual.resolve()))
        self.assertEqual(results["test.screen"]["test"], "testScreen")

    def test_readiness_failure_retains_precomparison_diagnostic(self):
        self.case = {"status": "failed", "failureMessage": "Rows are not ready"}
        results = self.export([{ "exportedFileName": self.actual.name,
                                "suggestedHumanReadableName": "readiness-test_0_UUID.screen"}])
        self.assertEqual(results["readiness:testScreen"]["diagnostic"], "Rows are not ready")
        self.assertNotIn("test.screen", results)

    def test_export_rejects_path_escape(self):
        with self.assertRaisesRegex(ValueError, "Invalid native attachment path"):
            self.export([{ "exportedFileName": "../outside.png", "suggestedHumanReadableName": "readiness-test"}])


if __name__ == "__main__":
    unittest.main()
