import json
from pathlib import Path
import re
import unittest


GITHUB = Path(__file__).resolve().parents[2]


class IosConsumerWorkflowTest(unittest.TestCase):
    def setUp(self):
        workflow = (GITHUB / "workflows/gradle-macos.yml").read_text()
        self.job = workflow.split("  wallet-core-consumer-tests:\n", 1)[1].split(
            "\n  enterprise-ios-mobile-tests:\n", 1
        )[0]
        self.steps = dict(
            (block.split("\n", 1)[0], block.split("\n", 1)[1])
            for block in self.job.split("      - name: ")[1:]
        )

    @staticmethod
    def timeout(block):
        return int(re.search(r"timeout-minutes: (\d+)", block)[1])

    @staticmethod
    def expression(block, field, flags, consumer=None):
        expression = re.search(rf"^\s*{field}: \$\{{\{{ (.+) \}}\}}$", block, re.M)[1]
        expression = re.sub(r"inputs\.([\w-]+)", r'flags["\1"]', expression)
        expression = expression.replace("matrix.consumer", "consumer")
        expression = expression.replace("&&", "and").replace("||", "or").replace("!cancelled()", "True")
        return eval(expression, {"__builtins__": {}}, {
            "flags": flags, "consumer": consumer, "fromJSON": json.loads,
            "always": lambda: True,
        })

    def test_selected_consumers_and_visuals_execute_once(self):
        # Each tuple is native functional, Compose functional, native visual.
        cases = {
            (False, False, False): [],
            (False, False, True): ["native"],
            (False, True, False): ["compose", "bridge"],
            (False, True, True): ["native", "compose", "bridge"],
            (True, False, False): ["native", "bridge"],
            (True, False, True): ["native", "bridge"],
            (True, True, False): ["native", "compose", "bridge"],
            (True, True, True): ["native", "compose", "bridge"],
        }
        for inputs, expected in cases.items():
            with self.subTest(inputs=inputs):
                flags = dict(zip(("run-native", "run-compose", "run-visual-native"), inputs))
                selected = self.expression(self.job.split("    steps:", 1)[0], "if", flags)
                self.assertEqual(bool(selected), bool(expected))
                if not selected:
                    continue
                consumers = self.expression(self.job, "consumer", flags)
                self.assertEqual(consumers, expected)
                self.assertEqual(len(consumers), len(set(consumers)))
                visual = [consumer for consumer in consumers if self.expression(
                    self.steps["Compare SwiftUI visual references using the shared framework"],
                    "if", flags, consumer,
                )]
                self.assertEqual(visual, ["native"] if flags["run-visual-native"] else [])
                for consumer in consumers:
                    functional = consumer == "bridge" or flags[f"run-{consumer}"]
                    for step in ("Configure gradle", "Run selected WalletCore consumer tests",
                                 "Collect and publish WalletCore consumer test reports",
                                 "Retain WalletCore consumer test reports"):
                        self.assertEqual(bool(self.expression(self.steps[step], "if", flags, consumer)),
                                         functional, (inputs, consumer, step))

    def test_each_functional_consumer_requires_distinct_reports(self):
        reporting = self.steps["Collect and publish WalletCore consumer test reports"]
        self.assertIn("test-report-require-tests: 'true'", reporting)
        self.assertIn("test-report-check-name: WalletCore ${{ matrix.consumer }} Test Report", reporting)
        for step in ("Retain WalletCore consumer test reports", "Retain WalletCore consumer phase logs",
                     "Retain incomplete WalletCore test results"):
            name = re.search(r"^          name: (.+)$", self.steps[step], re.M)[1]
            self.assertIn("${{ matrix.consumer }}", name)
            self.assertIn("${{ github.run_attempt }}", name)

    def test_test_deadline_cannot_prevent_report_collection(self):
        execution = self.steps["Run selected WalletCore consumer tests"]
        reporting = self.steps["Collect and publish WalletCore consumer test reports"]
        self.assertIn("collect-test-reports: 'false'", execution)
        self.assertIn("if: ${{ always() &&", reporting)
        self.assertIn("uses: ./waltid-identity/.github/actions/ios-test-reports", reporting)
        # Other callers retain their existing in-action reporting.
        action = (GITHUB / "actions/gradle-ios/action.yml").read_text()
        reporting_input = action.split("  collect-test-reports:\n", 1)[1].split(
            "  test-report-job-name:\n", 1
        )[0]
        self.assertIn("default: 'true'", reporting_input)

    def test_consumer_evidence_is_retained_before_visual_comparison(self):
        order = list(self.steps)
        execution = order.index("Run selected WalletCore consumer tests")
        comparison = order.index("Compare SwiftUI visual references using the shared framework")
        for name in (
            "Collect and publish WalletCore consumer test reports",
            "Retain WalletCore consumer test reports",
            "Retain WalletCore consumer phase logs",
            "Retain incomplete WalletCore test results",
        ):
            with self.subTest(step=name):
                self.assertLess(execution, order.index(name))
                self.assertLess(order.index(name), comparison)
        self.assertIn("always()", self.steps["Retain WalletCore consumer test reports"])
        phase_logs = self.steps["Retain WalletCore consumer phase logs"]
        self.assertIn("if: always()", phase_logs)
        self.assertIn("name: wallet-core-${{ matrix.consumer }}-phase-logs-${{ github.run_attempt }}", phase_logs)
        raw_results = self.steps["Retain incomplete WalletCore test results"]
        self.assertIn("if: failure() || cancelled()", raw_results)
        self.assertIn("waltid-identity/**/*.xcresult", raw_results)
        self.assertIn("/tmp/xcode-derived/Logs/Test/**/*.xcresult", raw_results)

    def test_job_reserves_reporting_comparison_and_upload_headroom(self):
        deadlines = sum(self.timeout(self.steps[name]) for name in (
            "Run selected WalletCore consumer tests",
            "Collect and publish WalletCore consumer test reports",
            "Compare SwiftUI visual references using the shared framework",
        ))
        self.assertGreaterEqual(self.timeout(self.job), deadlines + 10)


if __name__ == "__main__":
    unittest.main()
