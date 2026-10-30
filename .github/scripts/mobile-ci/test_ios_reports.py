import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import textwrap
import unittest


ACTION = Path(__file__).resolve().parents[2] / "actions/ios-test-reports/action.yml"


class IosReportsTest(unittest.TestCase):
    def collect(self, report_kind, required=True):
        step = ACTION.read_text().split("    - name: Collect iOS test reports\n", 1)[1]
        script = textwrap.dedent(step.split("      run: |\n", 1)[1].split("\n    - name:", 1)[0])
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            checkout = root / "identity"
            checkout.mkdir()
            output, summary = root / "output", root / "summary"
            output.touch()
            summary.touch()
            tools = root / "bin"
            tools.mkdir()
            # Exercise the real collection script without Xcode or Homebrew.
            converter = tools / "xcresultparser"
            converter.write_text(
                "#!/bin/sh\n"
                "case \"$1\" in *Incomplete.xcresult) exit 1;; esac\n"
                "printf '%s\\n' '<testsuite tests=\"1\"><testcase name=\"passes\"/></testsuite>'\n"
            )
            converter.chmod(0o755)
            if report_kind == "unconvertible":
                (checkout / "Incomplete.xcresult").mkdir()
            elif report_kind in ("xcresult", "partial"):
                (checkout / "Complete.xcresult").mkdir()
                if report_kind == "partial":
                    (checkout / "Incomplete.xcresult").mkdir()
            elif report_kind == "junit":
                report = checkout / "module/build/test-results/iosSimulatorArm64Test/tests.xml"
                report.parent.mkdir(parents=True)
                report.write_text('<testsuite tests="1"><testcase name="passes"/></testsuite>')
            script = script.replace("${{ inputs.waltid-directory }}", str(checkout))
            script = script.replace("${{ inputs.test-report-require-tests }}", str(required).lower())
            # Do not inspect a developer's existing simulator results.
            script = script.replace("/tmp/xcode-derived/Logs/Test", str(root / "external-results"))
            env = {**os.environ, "PATH": str(tools) + os.pathsep + os.environ["PATH"],
                   "GITHUB_OUTPUT": str(output), "GITHUB_STEP_SUMMARY": str(summary)}
            result = subprocess.run(["bash", "-euo", "pipefail", "-c", script], env=env,
                                    capture_output=True, text=True, timeout=10)
            outputs = dict(line.split("=", 1) for line in output.read_text().splitlines())
            reports = list((checkout / "build/ios-test-reports").rglob("*.xml"))
            return result, outputs, len(reports)

    def test_missing_required_reports_fail_collection(self):
        result, outputs, count = self.collect("missing")
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(outputs["has_report"], "false")
        self.assertEqual(count, 0)

    def test_failed_conversion_cannot_satisfy_required_reports(self):
        result, outputs, count = self.collect("unconvertible")
        self.assertIn("Failed to convert", result.stdout)
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(outputs["has_report"], "false")
        self.assertEqual(count, 0)

    def test_optional_missing_reports_remain_successful(self):
        result, outputs, count = self.collect("missing", required=False)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(outputs["has_report"], "false")
        self.assertEqual(count, 0)

    def test_available_required_reports_are_collected(self):
        for kind in ("junit", "xcresult"):
            with self.subTest(kind=kind):
                result, outputs, count = self.collect(kind)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertEqual(outputs["has_report"], "true")
                self.assertEqual(count, 1)

    def test_completed_report_survives_an_incomplete_sibling_bundle(self):
        result, outputs, count = self.collect("partial")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("Failed to convert", result.stdout)
        self.assertEqual(outputs["has_report"], "true")
        self.assertEqual(count, 1)


class EnterpriseSelectionTest(unittest.TestCase):
    def verify(self, selection, *, sdk=True, batch=True, compose=False):
        workflow = ACTION.parents[2] / "workflows/gradle-macos.yml"
        step = workflow.read_text().split("      - name: Require selected Enterprise SDK and batch test execution\n", 1)[1]
        script = textwrap.dedent(step.split("        run: |\n", 1)[1].split("\n      - name:", 1)[0])
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            verifier = root / "waltid-identity/.github/scripts/mobile-ci/verify_test_results.py"
            verifier.parent.mkdir(parents=True)
            shutil.copyfile(Path(__file__).with_name("verify_test_results.py"), verifier)
            reports = root / "waltid-identity-enterprise/waltid-enterprise-integration-tests/build/ios-test-reports/xcresult"
            reports.mkdir(parents=True)
            suites = []
            if sdk:
                suites.append('<testsuite name="iosAppEnterpriseTests.xctest"><testcase classname="EnterpriseTests" name="sdk"/></testsuite>')
            if batch:
                suites.append('<testsuite name="iosAppUITests.xctest"><testcase classname="BatchIssuanceE2ETests" name="batch"/></testsuite>')
            (reports / "iosAppEnterpriseTests.xcresult.xml").write_text("<testsuites>" + "".join(suites) + "</testsuites>")
            if compose:
                (reports / "compose-batch.xcresult.xml").write_text(
                    '<testsuite name="iosAppUITests.xctest"><testcase classname="BatchIssuanceE2ETests" name="batch"/></testsuite>')
            return subprocess.run(["bash", "-euo", "pipefail", "-c", script], cwd=root,
                                  env={**os.environ, "ENTERPRISE_IOS_DEMOS": selection},
                                  capture_output=True, text=True, timeout=10)

    def test_native_selection_requires_both_sdk_and_batch_suites(self):
        result = self.verify("native")
        self.assertEqual(result.returncode, 0, result.stderr)
        for missing in ("sdk", "batch"):
            with self.subTest(missing=missing):
                result = self.verify("native", **{missing: False})
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("Expected suite did not execute", result.stderr)

    def test_all_selection_still_requires_both_demos(self):
        missing = self.verify("all")
        self.assertNotEqual(missing.returncode, 0)
        self.assertIn("No JUnit reports found", missing.stderr)
        complete = self.verify("all", compose=True)
        self.assertEqual(complete.returncode, 0, complete.stderr)

    def test_unknown_selection_fails_closed(self):
        result = self.verify("invalid", compose=True)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Enterprise demos must be all, native or compose", result.stdout)


if __name__ == "__main__":
    unittest.main()
