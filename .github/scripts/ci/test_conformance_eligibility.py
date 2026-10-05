import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import textwrap
import unittest


GITHUB = Path(__file__).resolve().parents[2]
BUILD = GITHUB / "workflows/build.yml"
CONFORMANCE = GITHUB / "workflows/conformance-eligibility.yml"
PLATFORM = GITHUB / "workflows/platform-eligibility.yml"
DEFERRED = "ci:conformance-deferred"
OPT_INS = ("ci:conformance", "ci:issuer-conformance")


def step_script(workflow, name):
    step = workflow.read_text().split(f"      - name: {name}\n", 1)[1]
    return textwrap.dedent(step.split("        run: |\n", 1)[1].split("\n      - ", 1)[0])


class ConformanceEligibilityTest(unittest.TestCase):
    def decide(self, labels=(), *, event="pull_request", draft=False,
               fork=False, file="waltid-services/waltid-issuer-api2/src/main/Example.kt",
               ref="refs/heads/main"):
        wrapper = CONFORMANCE.read_text()
        expression = re.search(r"pr-require-label: (.+)", wrapper).group(1)
        self.assertEqual(expression, "${{ contains(fromJson(inputs.pr-labels), 'ci:conformance-deferred') }}")
        platform_name = re.search(r"      platform-name: (.+)", wrapper).group(1)
        platform_label = re.search(r"      platform-label: (.+)", wrapper).group(1)
        additional_labels = json.loads(re.search(r"      additional-labels: '(.+)'", wrapper).group(1))
        patterns = textwrap.dedent(wrapper.split("      path-patterns: |\n", 1)[1])
        json.loads(patterns)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output, calls = root / "output", root / "calls"
            output.touch()
            gh = root / "gh"
            gh.write_text('#!/bin/sh\necho called >> "$GH_CALL_LOG"\nprintf "%s\\n" "$CHANGED_FILE"\n')
            gh.chmod(0o755)
            env = {**os.environ, "PATH": str(root) + os.pathsep + os.environ["PATH"],
                   "GH_TOKEN": "", "GH_CALL_LOG": str(calls), "CHANGED_FILE": file,
                   "GITHUB_OUTPUT": str(output), "GITHUB_REPOSITORY": "walt-id/waltid-identity",
                   "EVENT_NAME": event, "REF": ref, "PR_NUMBER": "1",
                   "PR_HEAD_REPO": "contributor/fork" if fork else "walt-id/waltid-identity",
                   "PR_DRAFT": str(draft).lower(), "PR_LABELS": json.dumps(labels),
                   "PR_REQUIRE_LABEL": str(DEFERRED in labels).lower(),
                   "PLATFORM_NAME": platform_name, "PLATFORM_LABEL": platform_label,
                   "ADDITIONAL_LABELS": json.dumps(additional_labels), "PATH_PATTERNS": patterns}
            result = subprocess.run(
                ["bash", "-c", step_script(PLATFORM, "Decide whether platform jobs should run")],
                env=env, capture_output=True, text=True, timeout=10,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            return dict(line.split("=", 1) for line in output.read_text().splitlines()), calls.exists()

    def test_default_preserves_automatic_path_selection(self):
        for file, expected in [("waltid-services/waltid-issuer-api2/src/main/Example.kt", "true"),
                               ("README.md", "false")]:
            with self.subTest(file=file):
                output, called = self.decide(file=file)
                self.assertEqual(output["should-run"], expected)
                self.assertTrue(called)

    def test_deferral_skips_relevant_paths_without_querying_files(self):
        output, called = self.decide([DEFERRED])
        self.assertEqual(output["should-run"], "false")
        self.assertIn("requires ci:conformance or ci:issuer-conformance label", output["reason"])
        self.assertFalse(called)

    def test_both_opt_ins_override_deferral_and_draft(self):
        for label in OPT_INS:
            for draft in (False, True):
                with self.subTest(label=label, draft=draft):
                    output, called = self.decide([DEFERRED, label], draft=draft, file="README.md")
                    self.assertEqual(output["should-run"], "true")
                    self.assertEqual(output["reason"], f"{label} label")
                    self.assertFalse(called)

    def test_removing_deferral_triggers_and_restores_automatic_selection(self):
        types = re.search(r"types: \[([^]]+)\]", BUILD.read_text()).group(1).split(",")
        self.assertIn("unlabeled", [value.strip() for value in types])
        deferred, _ = self.decide([DEFERRED])
        resumed, called = self.decide([])
        self.assertEqual(deferred["should-run"], "false")
        self.assertEqual(resumed["should-run"], "true")
        self.assertTrue(called)

    def test_draft_and_fork_restrictions_remain(self):
        for labels, draft, fork in [([], True, False), ([DEFERRED], True, False),
                                    ([DEFERRED, OPT_INS[0]], False, True)]:
            with self.subTest(labels=labels, draft=draft, fork=fork):
                output, called = self.decide(labels, draft=draft, fork=fork)
                self.assertEqual(output["should-run"], "false")
                self.assertFalse(called)

    def test_deferral_does_not_change_main_manual_or_workflow_call(self):
        for event in ("push", "workflow_dispatch", "workflow_call"):
            with self.subTest(event=event):
                output, called = self.decide([DEFERRED], event=event)
                self.assertEqual(output["should-run"], "true")
                self.assertFalse(called)
        output, _ = self.decide([DEFERRED], event="push", ref="refs/heads/topic")
        self.assertEqual(output["should-run"], "false")

    def summary(self, selected, deferred, reason):
        with tempfile.TemporaryDirectory() as directory:
            summary = Path(directory) / "summary"
            result = subprocess.run(
                ["bash", "-c", step_script(BUILD, "Summarize live conformance selection")],
                env={**os.environ, "GITHUB_STEP_SUMMARY": str(summary),
                     "SELECTED": selected, "DEFERRED": str(deferred).lower(), "REASON": reason},
                capture_output=True, text=True, timeout=10,
            )
            self.assertEqual(result.returncode, 0, result.stderr)
            return summary.read_text()

    def test_summary_explains_deferred_coverage_and_completion(self):
        summary = self.summary("false", True, "PR requires an opt-in label")
        self.assertIn("**Deferred for this run**", summary)
        self.assertIn("not evidence that live conformance passed", summary)
        self.assertIn("passing run for the integrated revision before it enters `main`", summary)
        self.assertIn("Selection reason: PR requires an opt-in label", summary)

    def test_summary_distinguishes_requested_and_ordinary_skipped_coverage(self):
        for selected, deferred, reason in [("true", True, "ci:conformance label"),
                                           ("false", False, "no relevant paths")]:
            with self.subTest(selected=selected):
                summary = self.summary(selected, deferred, reason)
                self.assertNotIn("**Deferred for this run**", summary)
                self.assertIn(f"Requested: `{selected}`", summary)
                self.assertIn(f"Selection reason: {reason}", summary)

    def test_current_failed_or_cancelled_lanes_still_fail_the_gate(self):
        for result, expected in [("success", 0), ("skipped", 0), ("failure", 1), ("cancelled", 1)]:
            with self.subTest(result=result):
                gate = subprocess.run(
                    ["bash", "-c", step_script(BUILD, "Evaluate merge gate")],
                    env={**os.environ, "NEEDS": json.dumps({"gradle-build": {"result": result}})},
                    capture_output=True, text=True, timeout=10,
                )
                self.assertEqual(gate.returncode, expected, gate.stderr)


if __name__ == "__main__":
    unittest.main()
