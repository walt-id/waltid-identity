import os
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest


ACTION = Path(__file__).resolve().parents[2] / "actions/gradle-ios/action.yml"


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
            converter.write_text("#!/bin/sh\nexit 1\n")
            converter.chmod(0o755)
            if report_kind == "unconvertible":
                (checkout / "Tests.xcresult").mkdir()
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
        result, outputs, count = self.collect("junit")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(outputs["has_report"], "true")
        self.assertEqual(count, 1)


if __name__ == "__main__":
    unittest.main()
