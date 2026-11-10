"""The release docs job must reuse the same WalletCore producer as main CI."""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[3]
GITHUB = ROOT / ".github"
XCODE = "/Applications/Xcode_27.2.app/Contents/Developer"
CHECKOUT = (
    "walt-id/waltid-identity/.github/actions/checkout-repos@"
    "cba255efcba637f429422c132ccaf46b13b9572b"
)


def job_block(workflow, job_id):
    block = workflow.split(f"  {job_id}:\n", 1)[1]
    return re.split(r"(?m)^  [A-Za-z0-9_-]+:", block, maxsplit=1)[0]


class WalletCoreFrameworkWorkflowTest(unittest.TestCase):
    def setUp(self):
        self.producer = (GITHUB / "workflows/wallet-core-framework.yml").read_text()
        self.macos = (GITHUB / "workflows/gradle-macos.yml").read_text()
        self.release = (GITHUB / "workflows/release-candidate.yml").read_text()
        self.docs = (GITHUB / "workflows/mobile-sdk-docs.yml").read_text()
        self.action = (GITHUB / "actions/gradle-mobile-sdk-docs/action.yml").read_text()

    def test_producer_is_the_only_release_link_and_keeps_the_ci_contract(self):
        self.assertIn("workflow_call:", self.producer)
        self.assertIn("checkout-refs:", self.producer)
        self.assertIn("default: ''", self.producer)
        self.assertIn(
            "checkout-refs: ${{ steps.revisions.outputs.refs }}", self.producer
        )
        self.assertIn(
            "artifact-name: wallet-core-release-${{ github.run_attempt }}",
            self.producer,
        )
        self.assertIn(
            "value: ${{ jobs.wallet-core-framework.outputs.checkout-refs }}",
            self.producer,
        )
        self.assertIn(
            "value: ${{ jobs.wallet-core-framework.outputs.artifact-name }}",
            self.producer,
        )
        self.assertIn("runs-on: xcode-27", self.producer)
        self.assertIn("timeout-minutes: 70", self.producer)
        self.assertIn("timeout-minutes: 60", self.producer)
        self.assertIn(f"DEVELOPER_DIR: {XCODE}", self.producer)
        self.assertIn("resolved-refs: ${{ inputs.checkout-refs }}", self.producer)
        self.assertIn(
            "identity-sha: ${{ github.event.pull_request.head.sha || github.sha }}",
            self.producer,
        )
        # Main moved mobile checkout to the scoped-credential pin. The extracted
        # producer must keep that pin, matching the docs workflow and macOS callers.
        self.assertIn(CHECKOUT, self.producer)
        self.assertIn(CHECKOUT, self.docs)
        self.assertIn(CHECKOUT, self.macos)
        self.assertIn("--max-workers=1", self.producer)
        self.assertIn(
            "assembleWalletCoreReleaseXCFramework -PenableIosBuild=true --max-workers=1",
            self.producer,
        )
        self.assertIn("wallet-core-artifact.py pack", self.producer)
        self.assertEqual(self.producer.count("assembleWalletCoreReleaseXCFramework"), 1)

    def test_main_ci_calls_the_producer_without_changing_downstream_outputs(self):
        framework = job_block(self.macos, "wallet-core-framework")
        self.assertIn("uses: ./.github/workflows/wallet-core-framework.yml", framework)
        self.assertIn("secrets: inherit", framework)
        self.assertNotIn("with:", framework)
        self.assertNotIn("assembleWalletCoreReleaseXCFramework", framework)
        self.assertIn(
            "inputs.run-native || inputs.run-compose || inputs.run-enterprise || "
            "inputs.run-sdk-docs || inputs.run-visual-native",
            framework,
        )
        docs = self.macos.split("  macos-sdk-docs:\n", 1)[1]
        self.assertIn("needs: wallet-core-framework", docs)
        self.assertIn(
            "checkout-refs: ${{ needs.wallet-core-framework.outputs.checkout-refs }}",
            docs,
        )
        self.assertIn(
            "wallet-core-artifact: ${{ needs.wallet-core-framework.outputs.artifact-name }}",
            docs,
        )
        self.assertIn(f"DEVELOPER_DIR: {XCODE}", self.macos)

    def test_release_docs_depend_on_the_tagged_framework_build(self):
        framework = job_block(self.release, "wallet-core-framework")
        docs = self.release.split("  sdk-docs:\n", 1)[1].split("\n  deploy-license:\n", 1)[0]
        self.assertIn("needs: tag", framework)
        self.assertIn("uses: ./.github/workflows/wallet-core-framework.yml", framework)
        self.assertIn("secrets: inherit", framework)
        self.assertIn(
            "checkout-refs: ${{ needs.tag.outputs.checkout-refs }}", framework
        )
        self.assertIn("needs: [ version, tag, wallet-core-framework ]", docs)
        self.assertIn(
            "checkout-refs: ${{ needs.wallet-core-framework.outputs.checkout-refs }}",
            docs,
        )
        self.assertIn(
            "wallet-core-artifact: ${{ needs.wallet-core-framework.outputs.artifact-name }}",
            docs,
        )
        self.assertIn("deploy: true", docs)
        self.assertNotIn("assembleWalletCoreReleaseXCFramework", self.release)
        # promote.yml retags the image this job publishes; it does not rebuild docs.
        promote = (GITHUB / "workflows/promote.yml").read_text()
        self.assertNotIn("mobile-sdk-docs.yml", promote)

    def test_docs_restore_skips_the_link_and_docc_uses_that_framework(self):
        macos_docs = self.docs.split("  mobile-sdk-docs:\n", 1)[1].split("\n  publish:\n", 1)[0]
        restore = macos_docs.index("Restore the shared release framework")
        verify = macos_docs.index("Verify the shared release framework")
        generate = macos_docs.index("Generate mobile SDK API docs")
        self.assertLess(restore, verify)
        self.assertLess(verify, generate)
        self.assertIn("timeout-minutes: 75", macos_docs.split("Generate mobile SDK API docs", 1)[1])
        self.assertNotIn("timeout-minutes: 45", macos_docs)
        self.assertIn("name: ${{ inputs.wallet-core-artifact }}", macos_docs)
        self.assertIn("path: ${{ runner.temp }}/wallet-core-artifact", macos_docs)
        self.assertIn(
            'wallet-core-artifact.py restore --artifact-dir "$RUNNER_TEMP/wallet-core-artifact"',
            macos_docs,
        )
        self.assertIn(
            "skip-wallet-core-assemble: ${{ inputs.wallet-core-artifact != '' && 'true' || 'false' }}",
            macos_docs,
        )
        self.assertIn(f"DEVELOPER_DIR: {XCODE}", macos_docs)
        self.assertIn("needs: mobile-sdk-docs", self.docs.split("  publish:\n", 1)[1])

        assemble = self.action.split("Assemble Swift wallet SDK core XCFramework", 1)[1]
        docc = self.action.split("Generate Swift WalletSDK DocC archive", 1)[1]
        self.assertLess(
            self.action.index("Assemble Swift wallet SDK core XCFramework"),
            self.action.index("Generate Swift WalletSDK DocC archive"),
        )
        self.assertIn("skip-wallet-core-assemble != 'true'", assemble)
        self.assertIn("--max-workers=1", assemble)
        self.assertIn("generate-docc.sh", docc)

        framework = (
            "waltid-libraries/protocols/waltid-openid4vc-wallet-mobile/build/"
            "XCFrameworks/release/WalletCore.xcframework"
        )
        artifact = (GITHUB / "scripts/mobile-ci/wallet-core-artifact.py").read_text()
        package = (
            ROOT / "waltid-libraries/protocols/waltid-wallet-sdk-ios/Package.swift"
        ).read_text()
        self.assertIn(framework, artifact)
        self.assertIn(
            "../waltid-openid4vc-wallet-mobile/build/XCFrameworks/release/WalletCore.xcframework",
            package,
        )


if __name__ == "__main__":
    unittest.main()
