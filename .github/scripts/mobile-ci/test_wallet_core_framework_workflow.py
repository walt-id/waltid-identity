"""The release docs job must consume the shared WalletCore producer."""

from pathlib import Path
import unittest

import yaml


ROOT = Path(__file__).resolve().parents[3]
WORKFLOWS = ROOT / ".github/workflows"
ACTIONS = ROOT / ".github/actions"


def load_workflow(name):
    data = yaml.safe_load((WORKFLOWS / name).read_text())
    # PyYAML 1.1 parses the workflow key "on" as boolean True.
    if True in data and "on" not in data:
        data["on"] = data.pop(True)
    return data


class WalletCoreFrameworkWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.producer = load_workflow("wallet-core-framework.yml")
        self.release = load_workflow("release-candidate.yml")
        self.macos = load_workflow("gradle-macos.yml")
        self.docs = load_workflow("mobile-sdk-docs.yml")
        self.action = yaml.safe_load((ACTIONS / "gradle-mobile-sdk-docs/action.yml").read_text())

    def test_producer_is_the_single_serialized_framework_build(self):
        call = self.producer["on"]["workflow_call"]
        self.assertIn("checkout-refs", call["inputs"])
        self.assertEqual(call["inputs"]["checkout-refs"]["default"], "")
        self.assertEqual(
            set(call["outputs"]),
            {"checkout-refs", "artifact-name"},
        )
        self.assertEqual(
            call["outputs"]["artifact-name"]["value"],
            "${{ jobs.wallet-core-framework.outputs.artifact-name }}",
        )
        self.assertEqual(
            call["outputs"]["checkout-refs"]["value"],
            "${{ jobs.wallet-core-framework.outputs.checkout-refs }}",
        )
        job = self.producer["jobs"]["wallet-core-framework"]
        self.assertEqual(job["runs-on"], "xcode-27")
        self.assertEqual(job["timeout-minutes"], 70)
        self.assertEqual(
            job["outputs"]["artifact-name"],
            "wallet-core-release-${{ github.run_attempt }}",
        )
        self.assertEqual(
            self.producer["env"]["DEVELOPER_DIR"],
            "/Applications/Xcode_27.0.app/Contents/Developer",
        )
        build = next(step for step in job["steps"] if step["name"] == "Build the full release WalletCore")
        self.assertEqual(build["timeout-minutes"], 60)
        self.assertIn("--max-workers=1", build["run"])
        self.assertIn("assembleWalletCoreReleaseXCFramework", build["run"])
        upload = next(step for step in job["steps"] if step["name"] == "Publish the full release framework")
        self.assertEqual(upload["with"]["name"], "wallet-core-release-${{ github.run_attempt }}")
        checkout = next(step for step in job["steps"] if step["name"] == "Checkout all repos with ref fallback")
        self.assertEqual(checkout["with"]["resolved-refs"], "${{ inputs.checkout-refs }}")

    def test_macos_ci_and_release_call_the_same_producer(self):
        macos_job = self.macos["jobs"]["wallet-core-framework"]
        release_job = self.release["jobs"]["wallet-core-framework"]
        self.assertEqual(macos_job["uses"], "./.github/workflows/wallet-core-framework.yml")
        self.assertEqual(release_job["uses"], macos_job["uses"])
        self.assertNotIn("steps", macos_job)
        self.assertNotIn("steps", release_job)
        self.assertEqual(release_job["needs"], ["version", "tag"])
        self.assertEqual(
            release_job["with"]["checkout-refs"],
            "${{ needs.tag.outputs.checkout-refs }}",
        )

    def test_release_docs_restore_the_producer_artifact(self):
        docs = self.release["jobs"]["sdk-docs"]
        self.assertEqual(docs["needs"], ["version", "tag", "wallet-core-framework"])
        self.assertEqual(docs["uses"], "./.github/workflows/mobile-sdk-docs.yml")
        self.assertEqual(
            docs["with"]["wallet-core-artifact"],
            "${{ needs.wallet-core-framework.outputs.artifact-name }}",
        )
        self.assertEqual(
            docs["with"]["checkout-refs"],
            "${{ needs.wallet-core-framework.outputs.checkout-refs }}",
        )
        self.assertTrue(docs["with"]["deploy"])
        # Publishing the docs image still waits for this job, which now waits for the framework.
        self.assertIn("sdk-docs", self.release["jobs"]["deploy-license"]["needs"])
        macos_docs = self.macos["jobs"]["macos-sdk-docs"]
        self.assertEqual(macos_docs["needs"], "wallet-core-framework")
        self.assertEqual(
            macos_docs["with"]["wallet-core-artifact"],
            docs["with"]["wallet-core-artifact"],
        )
        self.assertEqual(
            macos_docs["with"]["checkout-refs"],
            docs["with"]["checkout-refs"],
        )

    def test_docs_workflow_restores_framework_before_docc_and_skips_the_link(self):
        self.assertEqual(
            self.docs["jobs"]["mobile-sdk-docs"]["env"]["DEVELOPER_DIR"],
            self.producer["env"]["DEVELOPER_DIR"],
        )
        self.assertIn("wallet-core-artifact", self.docs["on"]["workflow_call"]["inputs"])
        self.assertIn("checkout-refs", self.docs["on"]["workflow_call"]["inputs"])
        steps = self.docs["jobs"]["mobile-sdk-docs"]["steps"]
        names = [step["name"] for step in steps]
        restore = names.index("Restore the shared release framework")
        verify = names.index("Verify the shared release framework")
        generate = names.index("Generate mobile SDK API docs")
        self.assertLess(restore, verify)
        self.assertLess(verify, generate)
        self.assertEqual(steps[generate]["timeout-minutes"], 75)
        self.assertLess(steps[generate]["timeout-minutes"], self.docs["jobs"]["mobile-sdk-docs"]["timeout-minutes"])
        self.assertEqual(
            steps[restore]["with"]["name"],
            "${{ inputs.wallet-core-artifact }}",
        )
        self.assertEqual(
            steps[generate]["with"]["skip-wallet-core-assemble"],
            "${{ inputs.wallet-core-artifact != '' && 'true' || 'false' }}",
        )
        action_steps = self.action["runs"]["steps"]
        assemble = next(step for step in action_steps if step["name"] == "Assemble Swift wallet SDK core XCFramework")
        docc = next(step for step in action_steps if step["name"] == "Generate Swift WalletSDK DocC archive")
        self.assertEqual(assemble["if"], "${{ inputs.skip-wallet-core-assemble != 'true' }}")
        self.assertIn("--max-workers=1", assemble["run"])
        self.assertNotIn("if", docc)
        self.assertIn("generate-docc.sh", docc["run"])
        # DocC's binary target and the artifact restore path are the same release framework.
        package = (ROOT / "waltid-libraries/protocols/waltid-wallet-sdk-ios/Package.swift").read_text()
        artifact = (ROOT / ".github/scripts/mobile-ci/wallet-core-artifact.py").read_text()
        framework = "waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/XCFrameworks/release/WalletCore.xcframework"
        self.assertIn(framework, artifact)
        self.assertIn("../waltid-openid4vc-wallet-mobile/build/XCFrameworks/release/WalletCore.xcframework", package)


if __name__ == "__main__":
    unittest.main()
