"""Check selection and evidence routing in the actual consumer matrix expressions."""
import json
from pathlib import Path
import re
import unittest


GITHUB = Path(__file__).resolve().parents[2]
WORKFLOW = (GITHUB / "workflows/gradle-macos.yml").read_text()
JOB = WORKFLOW.split("  wallet-core-consumer-tests:\n", 1)[1].split("\n  enterprise-ios-mobile-tests:", 1)[0]
FLAGS = {
    "run-native-identity-document-provider-checks": "native-provider",
    "run-native-demo-tests": "native-demo",
    "run-compose-identity-document-provider-checks": "compose-provider",
    "run-compose-demo-tests": "compose-demo",
    "run-wallet-sdk-tests": "bridge",
}


def field(name):
    return re.search(r"^ +" + re.escape(name) + r": (.+)$", JOB, re.MULTILINE)[1]


def evaluate(expression, native=False, compose=False, consumer=""):
    # These workflow expressions use only JSON parsing, comparisons and boolean selection.
    expression = expression.removeprefix("${{").removesuffix("}}").strip()
    for source, target in [("inputs.run-native", "native"), ("inputs.run-compose", "compose"),
                           ("matrix.consumer", "consumer"), ("&&", " and "), ("||", " or ")]:
        expression = expression.replace(source, target)
    return eval(expression, {"__builtins__": {}, "fromJSON": json.loads},
                {"native": native, "compose": compose, "consumer": consumer})


class WalletCoreConsumersTest(unittest.TestCase):
    def test_every_selected_phase_runs_once_in_its_own_platform_job(self):
        cases = [
            (False, False, {}),
            (True, False, {"native": {"native-provider", "native-demo"}, "bridge": {"bridge"}}),
            (False, True, {"compose": {"compose-provider", "compose-demo"}, "bridge": {"bridge"}}),
            (True, True, {"native": {"native-provider", "native-demo"},
                          "compose": {"compose-provider", "compose-demo"}, "bridge": {"bridge"}}),
        ]
        for native, compose, expected in cases:
            with self.subTest(native=native, compose=compose):
                enabled = evaluate(field("if"), native, compose)
                consumers = evaluate(field("consumer"), native, compose) if enabled else []
                self.assertEqual(len(consumers), len(set(consumers)), "Duplicate consumer job")
                actual = {consumer: {phase for flag, phase in FLAGS.items()
                                     if evaluate(field(flag), native, compose, consumer) == "true"}
                          for consumer in consumers}
                self.assertEqual(actual, expected)
                self.assertEqual(sum(map(len, actual.values())),
                                 len(set().union(*actual.values())), "Duplicate phase")

    def test_each_lane_uses_the_producer_revision_and_verified_release(self):
        self.assertEqual(field("needs"), "wallet-core-framework")
        self.assertEqual(field("resolved-refs"), "${{ needs.wallet-core-framework.outputs.checkout-refs }}")
        restore = JOB.split("- name: Restore the shared release framework\n", 1)[1].split("\n      - name:", 1)[0]
        self.assertIn("name: ${{ needs.wallet-core-framework.outputs.artifact-name }}", restore)
        self.assertIn('wallet-core-artifact.py restore --artifact-dir "$RUNNER_TEMP/wallet-core-artifact"', JOB)

    def test_siblings_finish_and_all_lanes_require_reports(self):
        self.assertEqual(field("fail-fast"), "false")
        self.assertEqual(field("test-report-require-tests"), "'true'")
        for name in ("Retain WalletCore consumer test reports", "Retain WalletCore consumer phase logs"):
            step = JOB.split(f"- name: {name}\n", 1)[1].split("\n      - name:", 1)[0]
            self.assertIn("if: always()", step)
        self.assertNotIn("continue-on-error:", JOB)

    def test_parallel_artifacts_and_reports_have_distinct_names(self):
        values = [field(name) for name in ("test-result-artifact-name", "test-report-job-name",
                                           "test-report-check-name")]
        for step in JOB.split("uses: actions/upload-artifact@v7\n")[1:]:
            values.append(re.search(r"^ +name: (.+)$", step, re.MULTILINE)[1])
        for value in values:
            self.assertEqual(len({value.replace("${{ matrix.consumer }}", consumer)
                                  for consumer in ("native", "compose", "bridge")}), 3, value)


if __name__ == "__main__":
    unittest.main()
