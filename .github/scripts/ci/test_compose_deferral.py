import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

from test_conformance_eligibility import step_script


GITHUB = Path(__file__).resolve().parents[2]
DEFERRED = "ci:compose-deferred"
COMPOSE_GROUPS = ("ios-simulator", "compose", "visual-android", "visual-compose", "visual-browser")
NATIVE_GROUPS = ("native", "enterprise", "sdk-docs", "visual-native")


class ComposeDeferralTest(unittest.TestCase):
    def decide(self, platform, labels=(), *, event="pull_request", draft=False,
               fork=False, gh_exit=0, expected_exit=0):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            output = root / "output"
            output.touch()
            gh = root / "gh"
            # A workflow edit normally selects every mobile consumer.
            gh.write_text('#!/bin/sh\nprintf "%s\\n" ".github/workflows/build.yml"\nexit "$FAKE_GH_EXIT_CODE"\n')
            gh.chmod(0o755)
            env = {**os.environ, "PATH": str(root) + os.pathsep + os.environ["PATH"],
                   "GH_TOKEN": "", "GITHUB_OUTPUT": str(output), "FAKE_GH_EXIT_CODE": str(gh_exit),
                   "EVENT_NAME": event, "REF": "refs/heads/main", "PR_NUMBER": "1",
                   "GITHUB_REPOSITORY": "walt-id/waltid-identity",
                   "PR_HEAD_REPO": "contributor/fork" if fork else "walt-id/waltid-identity",
                   "PR_DRAFT": str(draft).lower(), "PR_LABELS": json.dumps(labels)}
            if platform == "macos":
                command = ["bash", str(GITHUB / "scripts/ci/decide-macos-lanes.sh")]
            else:
                wrapper = (GITHUB / "workflows/android-eligibility.yml").read_text()
                env.update({
                    "PLATFORM_NAME": "Android", "PLATFORM_LABEL": "ci:android",
                    "ADDITIONAL_LABELS": '["ci:mobile"]', "PR_REQUIRE_LABEL": "false",
                    "PR_EXCLUDE_LABEL": re.search(r"pr-exclude-label: (.+)", wrapper).group(1),
                    # Exercise the real selector with a relevant Android change.
                    "PATH_PATTERNS": '[".github/workflows/*"]',
                })
                command = ["bash", "-c", step_script(GITHUB / "workflows/platform-eligibility.yml",
                                                    "Decide whether platform jobs should run")]
            result = subprocess.run(command, env=env, capture_output=True, text=True, timeout=10)
            self.assertEqual(result.returncode, expected_exit, result.stderr)
            return dict(line.split("=", 1) for line in output.read_text().splitlines())

    def test_ready_pr_defers_all_compose_consumers_and_keeps_native_coverage(self):
        for labels in ([DEFERRED], [DEFERRED, "ci:mobile"], [DEFERRED, "ci:visual"]):
            with self.subTest(labels=labels):
                output = self.decide("macos", labels)
                for lane in COMPOSE_GROUPS:
                    self.assertEqual(output[f"run-{lane}"], "false", lane)
                for lane in NATIVE_GROUPS:
                    self.assertEqual(output[f"run-{lane}"], "true", lane)
                self.assertEqual(output["enterprise-demos"], "native")
                self.assertEqual(output["should-run"], "true")
                self.assertIn("mixed Kotlin simulator group", output["reason"])

    def test_android_deferral_precedes_automatic_and_opt_in_selection(self):
        for labels in ([DEFERRED], [DEFERRED, "ci:android"], [DEFERRED, "ci:mobile"]):
            with self.subTest(labels=labels):
                output = self.decide("android", labels)
                self.assertEqual(output["should-run"], "false")
                self.assertIn(DEFERRED, output["reason"])

    def test_deferral_does_not_opt_a_draft_into_native_tests(self):
        for platform in ("macos", "android"):
            with self.subTest(platform=platform):
                self.assertEqual(self.decide(platform, [DEFERRED], draft=True)["should-run"], "false")

    def test_removing_deferral_restores_default_coverage(self):
        output = self.decide("macos")
        for lane in COMPOSE_GROUPS + NATIVE_GROUPS:
            self.assertEqual(output[f"run-{lane}"], "true", lane)
        self.assertEqual(output["enterprise-demos"], "all")
        self.assertEqual(self.decide("android")["should-run"], "true")
        self.assertIn("unlabeled", (GITHUB / "workflows/build.yml").read_text())

    def test_main_and_explicit_workflow_calls_keep_full_coverage(self):
        for event in ("push", "workflow_call", "workflow_dispatch"):
            with self.subTest(event=event):
                output = self.decide("macos", [DEFERRED], event=event)
                for lane in COMPOSE_GROUPS + NATIVE_GROUPS:
                    self.assertEqual(output[f"run-{lane}"], "true", lane)
                self.assertEqual(output["enterprise-demos"], "all")
                self.assertEqual(self.decide("android", [DEFERRED], event=event)["should-run"], "true")

    def test_forks_cannot_enable_native_jobs_through_deferral(self):
        for platform in ("macos", "android"):
            with self.subTest(platform=platform):
                self.assertEqual(self.decide(platform, [DEFERRED, "ci:mobile"], fork=True)["should-run"], "false")

    def test_deferral_does_not_hide_failed_native_path_selection(self):
        output = self.decide("macos", [DEFERRED], gh_exit=17, expected_exit=17)
        self.assertEqual(output, {})

    def test_workflow_bindings_preserve_the_selection_through_execution(self):
        macos = (GITHUB / "workflows/macos-eligibility.yml").read_text()
        self.assertIn("value: ${{ jobs.decide.outputs.enterprise-demos }}", macos)
        self.assertIn("enterprise-demos: ${{ steps.decide.outputs.enterprise-demos }}", macos)
        build = (GITHUB / "workflows/build.yml").read_text()
        self.assertIn("enterprise-demos: ${{ needs.macos-eligibility.outputs.enterprise-demos }}", build)
        platform = (GITHUB / "workflows/platform-eligibility.yml").read_text()
        self.assertIn("PR_EXCLUDE_LABEL: ${{ inputs.pr-exclude-label }}", platform)
        dc_api = (GITHUB / "workflows/android-dc-api-tests.yml").read_text()
        self.assertIn("github.event_name == 'push' || (", dc_api)
        self.assertIn("!contains(github.event.pull_request.labels.*.name, 'ci:compose-deferred')", dc_api)


if __name__ == "__main__":
    unittest.main()
