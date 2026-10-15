"""Negative contracts: a passing XCTest alone must never certify a visual comparison."""
import json
import contextlib
import io
import hashlib
from pathlib import Path
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

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

    def test_invalid_icc_profile_and_oversized_inputs_fail_closed(self):
        Image.new("RGB", (2, 3), "red").save(self.actual, icc_profile=b"invalid ICC profile")
        with self.assertRaisesRegex(ValueError, "Invalid visual PNG"):
            report.image_info(self.actual)
        with self.assertRaisesRegex(ValueError, "exceeds 10 MiB"):
            report.image_info(b"x" * (10 * 1024 * 1024 + 1))

    def test_compose_comparison_requires_run_and_configuration_binding(self):
        metadata = {**self.run, "id": self.state["id"], "renderer": "android", "kind": "deterministic",
                    "viewportWidth": 393, "viewportHeight": 852, "theme": "light", "scale": 1, "fontScale": 1}
        config = {"viewport": [393, 852], "theme": "light", "scale": 1, "fontScale": 1}
        def assess(value):
            return report.assess_compose(self.state, "android", self.case, value, self.baseline, self.run, config)[0]
        result = {"type": "unchanged", "context_data": metadata}
        self.assertEqual(assess(result), "passed")
        self.assertEqual(assess({"type": "unchanged"}), "stale")
        self.assertEqual(assess({**result, "context_data": {**metadata, "runId": "old"}}), "stale")
        self.assertEqual(assess({**result, "context_data": {**metadata, "renderer": "compose-ios"}}), "invalid")
        self.assertEqual(assess({**result, "context_data": {**metadata, "theme": "dark"}}), "invalid")
        self.assertEqual(assess({**result, "type": "recorded"}), "recorded")
        self.assertEqual(assess({**result, "type": "changed"}), "failed")

    def test_readiness_transitions_are_kept_with_the_compared_capture(self):
        receipt = self.root / "receipt.json"
        receipt.write_text(json.dumps(self.result))
        readiness = self.root / "readiness.json"
        observations = [{"beforeRender": False, "afterRender": True, "ready": True}]
        readiness.write_text(json.dumps({"id": self.state["id"], "observations": observations}))
        results = self.export([
            {"exportedFileName": receipt.name, "suggestedHumanReadableName": "wallet-visual-receipt-test.json"},
            {"exportedFileName": readiness.name, "suggestedHumanReadableName": "wallet-visual-readiness-test.json"}])
        self.assertEqual(results[self.state["id"]]["readiness"]["observations"], observations)

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


class NativeResultTests(unittest.TestCase):
    def tree(self, *cases):
        return {"testNodes": [{"nodeType": "Test Plan", "name": "iosApp", "children": [
            {"nodeType": "Test Case", "name": test + "()", "nodeIdentifier": f"WalletVisualTests/{test}()",
             "result": status, "durationInSeconds": 1.25} for test, status in cases]}]}

    def test_complete_xcresult_recovers_a_case_omitted_by_the_cli(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            bundle = root / "run.xcresult"
            bundle.mkdir()
            path = root / "native-results.json"
            path.write_text(json.dumps({"schema": "xcodebuildmcp.output.test-result", "schemaVersion": 3,
                "data": {"artifacts": {"xcresultPath": str(bundle)}, "testCases": []}}))
            run = {"runId": "current", "started": 0}
            tree = self.tree(("testAccessBiometricUnavailable", "Passed"))

            def xcresult(command, **kwargs):
                if command[2] == "get":
                    return SimpleNamespace(stdout=json.dumps(tree))
                export = root / "native-attachments" / run["runId"]
                export.mkdir(parents=True)
                (export / "receipt.json").write_text(json.dumps({"id": "access.unlock.biometric_unavailable"}))
                (export / "manifest.json").write_text(json.dumps([
                    {"testIdentifier": "WalletVisualTests/testAccessBiometricUnavailable()", "attachments": [
                        {"exportedFileName": "receipt.json", "suggestedHumanReadableName": "wallet-visual-receipt-access.json"}]}]))
                return SimpleNamespace(stdout="")

            catalogue = {"renderers": {"swiftui": {"suite": "WalletVisualTests"}}}
            with patch.object(report, "CATALOGUE", catalogue), patch.object(report.subprocess, "run", side_effect=xcresult):
                tests, receipts, error = report.native_tests(path, run, root)
            self.assertIsNone(error)
            self.assertEqual(tests["testAccessBiometricUnavailable"]["status"], "passed")
            self.assertEqual(receipts["access.unlock.biometric_unavailable"]["test"], "testAccessBiometricUnavailable")
            self.assertEqual(json.loads((root / "native-tests.json").read_text()), tree)

    def test_tree_preserves_failed_and_skipped_cases_and_filters_other_suites(self):
        tree = self.tree(("testPass", "Passed"), ("testFail", "Failed"), ("testSkip", "Skipped"))
        tree["testNodes"][0]["children"].append({"nodeType": "Test Case", "name": "testOther()",
            "nodeIdentifier": "OtherSuite/testOther()", "result": "Passed"})
        tests = report.native_test_cases(tree, "WalletVisualTests")
        self.assertEqual({name: case["status"] for name, case in tests.items()},
                         {"testPass": "passed", "testFail": "failed", "testSkip": "skipped"})
        self.assertEqual(tests["testPass"]["durationMs"], 1250)

    def test_duplicate_case_cannot_overwrite_a_failure_with_a_pass(self):
        with self.assertRaisesRegex(ValueError, "duplicate native test"):
            report.native_test_cases(self.tree(("testSame", "Failed"), ("testSame", "Passed")), "WalletVisualTests")

    def test_unrecognized_and_expected_failure_results_never_pass(self):
        for result in ("unknown", "Expected Failure", None):
            with self.subTest(result=result), self.assertRaisesRegex(ValueError, "Unsupported native test result"):
                report.native_test_cases(self.tree(("testScreen", result)), "WalletVisualTests")


class SourceBindingTests(unittest.TestCase):
    """Exercise begin/finish with real Git state and pixels; mock only native result transport."""

    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.workspace = Path(directory.name).resolve()
        self.root = self.workspace / "waltid-identity"
        self.root.mkdir()
        self.files = {
            "waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/src/commonMain/Wallet.kt": "class Wallet",
            "waltid-libraries/protocols/waltid-wallet-sdk-ios/Sources/WalletSDK/CredentialTitles.swift": "struct CredentialTitles {}",
            "build-logic/src/main/kotlin/waltid.mobile.library.gradle.kts": "// build input",
            "build.gradle.kts": "// build root",
            ".gitignore": "build/\nlocal.properties\n",
        }
        for name, text in self.files.items():
            path = self.root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text)
        self.here = self.root / "waltid-applications/waltid-wallet-demo-test-fixtures/visual"
        self.here.mkdir(parents=True)
        fixtures = self.here.parent / "resources/files/wallet-visual-data.json"
        fixtures.parent.mkdir(parents=True)
        fixtures.write_text("{}")
        self.baseline = self.root / "snapshots/test-screen.png"
        self.baseline.parent.mkdir()
        Image.new("RGB", (2, 3), "red").save(self.baseline)
        for command in (["init", "-q"], ["add", "."],
                        ["-c", "user.name=Visual test", "-c", "user.email=visual@example.test",
                         "-c", "commit.gpgsign=false", "commit", "-qm", "fixture"]):
            subprocess.run(["git", "-C", str(self.root), *command], check=True, capture_output=True)
        self.output = self.root / "build/report"
        catalogue = {"renderers": {"swiftui": {"baselineDirectory": "snapshots", "suffix": ""}},
                     "states": [{"id": "test.screen", "swiftTest": "testScreen", "requirements": ["Source-bound comparison"]}]}
        for name, value in (("ROOT", self.root), ("HERE", self.here), ("CATALOGUE", catalogue)):
            replacement = patch.object(report, name, value)
            replacement.start()
            self.addCleanup(replacement.stop)

    def begin(self, gradle_root=None):
        arguments = ["report.py", "begin", "--output", str(self.output), "--renderers", "swiftui",
                     "--gradle-root", str(gradle_root or self.root)]
        with patch.object(sys, "argv", arguments), contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(report.main(), 0)
        run = json.loads((self.output / "run.json").read_text())
        actual = self.output / "actual.png"
        Image.new("RGB", (2, 3), "red").save(actual)
        self.receipt = {**run, "schemaVersion": 1, "id": "test.screen", "test": "testScreen", "renderer": "swiftui",
                        "kind": "deterministic", "mode": "verify", "compared": True, "result": "passed",
                        "baselineDigest": report.digest(self.baseline), "actualDigest": report.digest(actual),
                        "actual_file_path": str(actual), "viewport": [2, 3], "scale": 1, "timezone": "UTC"}
        return run

    def finish(self, run):
        results = ({"testScreen": {"status": "passed"}}, {"test.screen": self.receipt}, None)
        with patch.object(report, "native_tests", return_value=results), contextlib.redirect_stdout(io.StringIO()):
            status = report.finish(SimpleNamespace(output=self.output, native_results=None), run)
        return status, json.loads((self.output / "manifest.json").read_text())

    def test_sdk_and_build_edits_make_otherwise_valid_comparisons_stale(self):
        for name, original in self.files.items():
            with self.subTest(path=name):
                run = self.begin()
                path = self.root / name
                path.write_text(original + "\n// changed during comparison")
                status, manifest = self.finish(run)
                self.assertEqual(status, 1)
                self.assertEqual(manifest["counts"], {"stale": 1})
                self.assertIn("Source or baselines changed after begin", manifest["errors"][0])
                path.write_text(original)

    def test_new_sdk_source_and_deleted_build_input_invalidate_the_run(self):
        added = self.root / "waltid-libraries/protocols/waltid-wallet-sdk-ios/Sources/WalletSDK/New.swift"
        deleted = self.root / "build-logic/src/main/kotlin/waltid.mobile.library.gradle.kts"
        for change in (lambda: added.write_text("struct New {}"), deleted.unlink):
            with self.subTest(change=change):
                run = self.begin()
                change()
                status, manifest = self.finish(run)
                self.assertEqual((status, manifest["counts"]), (1, {"stale": 1}))
                added.unlink(missing_ok=True)
                deleted.write_text(self.files[deleted.relative_to(self.root).as_posix()])

    def test_ignored_build_outputs_and_local_settings_do_not_invalidate_source(self):
        run = self.begin()
        (self.root / "build/compiled.bin").write_bytes(b"generated after begin")
        (self.root / "local.properties").write_text("sdk.dir=/synthetic/sdk")
        status, manifest = self.finish(run)
        self.assertEqual((status, manifest["counts"], manifest["errors"]), (0, {"passed": 1}, []))

    def test_dirty_input_inventory_can_reconstruct_the_exact_fingerprint(self):
        modified = self.root / "waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/src/commonMain/Wallet.kt"
        modified.write_text("class WalletChanged")
        backup = self.root / "gradle.properties.backup"
        backup.write_text("enableIosBuild=false\n")
        deleted = self.root / "build-logic/src/main/kotlin/waltid.mobile.library.gradle.kts"
        deleted.unlink()
        (self.root / "local.properties").write_text("sdk.dir=/synthetic/sdk")
        run = self.begin()
        inputs = self.output / "source-inputs.json"
        inventory = json.loads(inputs.read_text())
        self.assertTrue(run["dirty"])
        self.assertEqual(run["sourceInputsDigest"], report.digest(inputs))
        for key in ("runId", "sha", "fingerprint"):
            self.assertEqual(inventory[key], run[key])
        self.assertEqual(len(inventory["repositories"]), 1)
        repository = inventory["repositories"][0]
        self.assertEqual(repository["root"], str(self.root))
        files = {entry["path"]: entry for entry in repository["files"]}
        listed = subprocess.check_output(["git", "-C", str(self.root), "ls-files", "-z", "--cached", "--others", "--exclude-standard"], text=True)
        self.assertEqual(set(files), set(filter(None, listed.split("\0"))))
        self.assertNotIn("local.properties", files)
        for path in (modified, backup):
            entry = files[path.relative_to(self.root).as_posix()]
            self.assertEqual(entry, {"path": path.relative_to(self.root).as_posix(), "kind": "file", "sha256": report.digest(path)})
        self.assertEqual(files[deleted.relative_to(self.root).as_posix()]["kind"], "missing")
        rebuilt = hashlib.sha256()
        for repository in inventory["repositories"]:
            rebuilt.update(b"\0repository\0")
            for entry in repository["files"]:
                rebuilt.update(entry["path"].encode() + b"\0")
                rebuilt.update(bytes.fromhex(entry["sha256"]))
        self.assertEqual(rebuilt.hexdigest(), run["fingerprint"])
        self.assertEqual(self.finish(run)[0], 0)

    def test_missing_or_modified_input_inventory_cannot_certify_a_comparison(self):
        for tamper in (lambda path: path.unlink(), lambda path: path.write_text("{}")):
            with self.subTest(tamper=tamper):
                run = self.begin()
                tamper(self.output / "source-inputs.json")
                status, manifest = self.finish(run)
                self.assertEqual((status, manifest["counts"]), (1, {"stale": 1}))
                self.assertIn("Source input manifest is missing or changed after begin", manifest["errors"])

    def test_coordinated_build_inputs_are_bound_to_the_same_run(self):
        (self.workspace / ".gitignore").write_text("waltid-identity/\n")
        settings = self.workspace / "settings.gradle.kts"
        settings.write_text("// coordinated settings")
        subprocess.run(["git", "-C", str(self.workspace), "init", "-q"], check=True, capture_output=True)
        subprocess.run(["git", "-C", str(self.workspace), "add", "."], check=True, capture_output=True)
        run = self.begin(self.workspace)
        repositories = json.loads((self.output / "source-inputs.json").read_text())["repositories"]
        self.assertEqual([repository["root"] for repository in repositories], [str(self.root), str(self.workspace)])
        coordinated = {entry["path"]: entry for entry in repositories[1]["files"]}
        self.assertEqual(coordinated["settings.gradle.kts"]["sha256"], report.digest(settings))
        self.assertEqual(self.finish(run)[0], 0)
        settings.write_text("// changed coordinated settings")
        status, manifest = self.finish(run)
        self.assertEqual((status, manifest["counts"]), (1, {"stale": 1}))

    def test_run_from_the_old_fingerprint_contract_is_stale(self):
        for version in (2, 3):
            with self.subTest(version=version):
                run = self.begin()
                run["schemaVersion"] = version
                del run["gradleRoot"]
                status, manifest = self.finish(run)
                self.assertEqual((status, manifest["counts"]), (1, {"stale": 1}))


if __name__ == "__main__":
    unittest.main()
