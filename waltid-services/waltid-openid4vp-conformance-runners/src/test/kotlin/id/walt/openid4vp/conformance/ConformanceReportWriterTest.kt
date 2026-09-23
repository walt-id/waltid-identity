package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.report.ConformanceCiFlags
import id.walt.openid4vp.conformance.report.ConformanceReportFormat
import id.walt.openid4vp.conformance.report.ConformanceReportWriter
import id.walt.openid4vp.conformance.report.isAccepted
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ConformanceReportWriterTest {

    @Test
    fun writesSummaryAndResultsForRole() {
        val reportRoot = Files.createTempDirectory("openid-conformance-report").toString()
        val results = listOf(
            TestPlanResult(
                testName = "MdlBaseline",
                conformanceTestId = "abc-123",
                conformanceStatus = "FINISHED",
                conformanceResult = "PASSED",
            ),
            TestPlanResult(
                testName = "SdJwtHaip",
                conformanceTestId = "def-456",
                conformanceStatus = "FINISHED",
                conformanceResult = "FAILED",
                errorMessage = "audience mismatch",
            ),
        )

        ConformanceReportWriter.writeTestPlanResults(
            role = ConformanceReportWriter.Role.VP_VERIFIER,
            results = results,
            conformanceHost = "conformance.example",
            conformancePort = 443,
            reportRoot = reportRoot,
            allowFailure = true,
        )

        val summary = Files.readString(
            ConformanceReportWriter.reportDir(ConformanceReportWriter.Role.VP_VERIFIER, reportRoot)
                .resolve("summary.md")
        )
        assertTrue(summary.contains("MdlBaseline"))
        assertTrue(summary.contains("SdJwtHaip"))
        assertTrue(summary.contains("audience mismatch"))
        assertTrue(summary.contains("Soft-fail"))
        assertTrue(summary.contains("[log](https://conformance.example:443/log-detail.html?log=def-456)"))
        assertTrue(summary.contains("# OpenID4VP Verifier Conformance Summary"))
        assertTrue(summary.contains("| Test | Status | Suite | Log | Error |"))
        assertFalse(summary.contains("## Failed and skipped"))
        assertFalse(summary.contains("- Fix:"))
    }

    @Test
    fun writesTheSameSummaryShapeForWalletRoles() {
        val reportRoot = Files.createTempDirectory("openid-conformance-wallet-report").toString()
        val results = listOf(
            TestPlanResult(
                testName = "wallet-module",
                conformanceTestId = "wal-123",
                conformanceStatus = "FINISHED",
                conformanceResult = "PASSED",
                walletStatus = "PASSED",
            ),
        )

        listOf(ConformanceReportWriter.Role.VP_WALLET, ConformanceReportWriter.Role.VCI_WALLET)
            .forEach { role ->
                ConformanceReportWriter.writeTestPlanResults(
                    role = role,
                    results = results,
                    conformanceHost = "conformance.example",
                    conformancePort = 443,
                    reportRoot = reportRoot,
                    allowFailure = true,
                    producer = role.directoryName,
                )
                val summary = Files.readString(
                    ConformanceReportWriter.reportDir(role, reportRoot).resolve("summary.md")
                )
                assertTrue(summary.contains("# ${role.title} Conformance Summary"))
                assertTrue(summary.contains("| Test | Status | Suite | Log | Error |"))
                assertTrue(summary.contains("wallet-module"))
                assertTrue(summary.contains("Soft-fail"))
            }
    }

    @Test
    fun writeSkippedIfEmptyDoesNotOverwriteAnExistingReport() {
        val reportRoot = Files.createTempDirectory("openid-conformance-skipped").toString()
        ConformanceReportWriter.writeTestPlanResults(
            role = ConformanceReportWriter.Role.VP_WALLET,
            results = listOf(
                TestPlanResult(
                    testName = "real-module",
                    conformanceTestId = "abc",
                    conformanceResult = "PASSED",
                    walletStatus = "PASSED",
                )
            ),
            reportRoot = reportRoot,
            allowFailure = true,
            producer = "vp-wallet",
        )
        ConformanceReportWriter.writeSkippedIfEmpty(
            role = ConformanceReportWriter.Role.VP_WALLET,
            reason = "suite down",
            reportRoot = reportRoot,
        )
        val summary = Files.readString(
            ConformanceReportWriter.reportDir(ConformanceReportWriter.Role.VP_WALLET, reportRoot)
                .resolve("summary.md")
        )
        assertTrue(summary.contains("real-module"))
        assertFalse(summary.contains("suite down"))
    }

    @Test
    fun writeSkippedIfEmptyPublishesPlaceholderWhenMissing() {
        val reportRoot = Files.createTempDirectory("openid-conformance-placeholder").toString()
        ConformanceReportWriter.writeSkippedIfEmpty(
            role = ConformanceReportWriter.Role.VCI_WALLET,
            reason = "Conformance suite not available at localhost.emobix.co.uk:8443",
            reportRoot = reportRoot,
            allowFailure = true,
        )
        val summary = Files.readString(
            ConformanceReportWriter.reportDir(ConformanceReportWriter.Role.VCI_WALLET, reportRoot)
                .resolve("summary.md")
        )
        assertTrue(summary.contains("# OpenID4VCI Wallet Conformance Summary"))
        assertTrue(summary.contains("| Test | Status | Suite | Log | Error |"))
        assertTrue(summary.contains("conformance-suite"))
        assertTrue(summary.contains("not available"))
        assertFalse(summary.contains("## Failed and skipped"))
        assertFalse(summary.contains("- Fix:"))
    }

    @Test
    fun failIfNeededRespectsSoftFail() {
        val results = listOf(
            TestPlanResult(
                testName = "failing",
                conformanceTestId = "x",
                conformanceResult = "FAILED",
            )
        )
        ConformanceReportWriter.failIfNeededFromTestPlanResults(
            role = ConformanceReportWriter.Role.VP_VERIFIER,
            results = results,
            allowFailure = true,
        )
        assertFailsWith<IllegalStateException> {
            ConformanceReportWriter.failIfNeededFromTestPlanResults(
                role = ConformanceReportWriter.Role.VP_VERIFIER,
                results = results,
                allowFailure = false,
            )
        }
    }

    @Test
    fun compactWalletNamesStayInTheSameTable() {
        val reportRoot = Files.createTempDirectory("openid-conformance-compact").toString()
        val producerId =
            "oid4vp-1final-wallet-test-plan/client_id_prefix=x509_hash,credential_format=sd_jwt_vc," +
                "request_method=request_uri_signed,response_mode=direct_post.jwt,vp_profile=plain"
        ConformanceReportWriter.writeTestPlanResults(
            role = ConformanceReportWriter.Role.VP_WALLET,
            results = listOf(
                TestPlanResult(
                    testName = "$producerId/oid4vp-1final-wallet-happy-flow",
                    conformanceTestId = "pass-1",
                    conformanceResult = "PASSED",
                    walletStatus = "PASSED",
                ),
                TestPlanResult(
                    testName = "$producerId/oid4vp-1final-wallet-negative-test-missing-nonce",
                    conformanceTestId = "skip-1",
                    conformanceResult = "SKIPPED",
                    walletStatus = "SKIPPED",
                    skipReason = "Not applicable to this variant: no request_method value applies to this variant",
                ),
                TestPlanResult(
                    testName = "$producerId/oid4vp-1final-wallet-happy-flow-encrypted",
                    conformanceTestId = "fail-1",
                    conformanceResult = "FAILED",
                    walletStatus = "FAILED",
                    errorMessage = "audience mismatch",
                ),
            ),
            conformanceHost = "conformance.example",
            conformancePort = 443,
            reportRoot = reportRoot,
            allowFailure = true,
            producer = producerId,
        )

        val summary = Files.readString(
            ConformanceReportWriter.reportDir(ConformanceReportWriter.Role.VP_WALLET, reportRoot)
                .resolve("summary.md")
        )
        val compact = ConformanceReportFormat.displayName(
            "$producerId/oid4vp-1final-wallet-happy-flow",
        )
        assertEquals("happy-flow", compact.title)
        assertEquals(
            "sd_jwt_vc · x509_hash · request_uri_signed · direct_post.jwt · plain",
            compact.variant,
        )
        assertTrue(summary.contains("happy-flow"))
        assertTrue(summary.contains("negative-test-missing-nonce"))
        assertTrue(summary.contains(compact.variant!!))
        assertTrue(summary.contains("| Test | Status | Suite | Log | Error |"))
        assertTrue(summary.contains("`skipped`"))
        assertTrue(summary.contains("`failed`"))
        assertTrue(summary.contains("audience mismatch"))
        assertTrue(summary.contains("Not applicable to this variant"))
        assertFalse(summary.contains("## Failed and skipped"))
        assertFalse(summary.contains("- Fix:"))
        assertFalse(summary.contains("| `$producerId/oid4vp-1final-wallet-happy-flow` |"))
    }

    @Test
    fun vciTlsWarningIsAcceptedWhileKeepingTheSuiteResult() {
        val warning = TestPlanResult(
            testName = "oid4vci-1_0-wallet-test-credential-issuance",
            conformanceTestId = "warn-1",
            conformanceResult = "WARNING",
            walletStatus = "PASSED",
        )
        assertTrue(warning.isAccepted())
        assertTrue(warning.passed)
        assertEquals(null, warning.message)

        val reportRoot = Files.createTempDirectory("openid-conformance-vci-warning").toString()
        ConformanceReportWriter.writeTestPlanResults(
            role = ConformanceReportWriter.Role.VCI_WALLET,
            results = listOf(warning),
            conformanceHost = "conformance.example",
            conformancePort = 443,
            reportRoot = reportRoot,
            allowFailure = true,
        )
        val summary = Files.readString(
            ConformanceReportWriter.reportDir(ConformanceReportWriter.Role.VCI_WALLET, reportRoot)
                .resolve("summary.md")
        )
        assertTrue(summary.contains("`passed`"))
        assertTrue(summary.contains("WARNING"))
        assertFalse(summary.contains("`failed`"))
        assertFalse(summary.contains("Conformance: WARNING"))
    }

    @Test
    fun vpWalletReviewIsAcceptedWithoutAnErrorCell() {
        val review = TestPlanResult(
            testName = "oid4vp-1final-wallet-happy-flow",
            conformanceTestId = "review-1",
            conformanceResult = "REVIEW",
            walletStatus = "PASSED",
        )
        assertTrue(review.isAccepted())
        assertTrue(review.passed)

        val reportRoot = Files.createTempDirectory("openid-conformance-vp-review").toString()
        ConformanceReportWriter.writeTestPlanResults(
            role = ConformanceReportWriter.Role.VP_WALLET,
            results = listOf(review),
            conformanceHost = "conformance.example",
            conformancePort = 443,
            reportRoot = reportRoot,
            allowFailure = true,
        )
        val summary = Files.readString(
            ConformanceReportWriter.reportDir(ConformanceReportWriter.Role.VP_WALLET, reportRoot)
                .resolve("summary.md")
        )
        assertTrue(summary.contains("`passed`"))
        assertTrue(summary.contains("REVIEW"))
        assertFalse(summary.contains("`failed`"))
        assertFalse(summary.contains("Conformance: REVIEW"))
    }

    @Test
    fun interruptedWarningIsNotAcceptedAsAPass() {
        val interrupted = TestPlanResult(
            testName = "oid4vci-1_0-wallet-test-client-attestation-challenge",
            conformanceTestId = "int-1",
            conformanceStatus = "INTERRUPTED",
            conformanceResult = "WARNING",
            walletStatus = "WARNING",
            errorMessage = "Suite interrupted this module",
        )
        assertFalse(interrupted.isAccepted())
        assertFalse(interrupted.passed)

        val reportRoot = Files.createTempDirectory("openid-conformance-vci-interrupted").toString()
        ConformanceReportWriter.writeTestPlanResults(
            role = ConformanceReportWriter.Role.VCI_WALLET,
            results = listOf(interrupted),
            reportRoot = reportRoot,
            allowFailure = true,
        )
        val summary = Files.readString(
            ConformanceReportWriter.reportDir(ConformanceReportWriter.Role.VCI_WALLET, reportRoot)
                .resolve("summary.md")
        )
        assertTrue(summary.contains("`failed`"))
        assertTrue(summary.contains("Suite interrupted this module"))
    }

    @Test
    fun skippedFapi2ClientModuleIsAccepted() {
        val skipped = TestPlanResult(
            testName = "fapi2-security-profile-final-client-test-happy-path",
            conformanceTestId = "fapi-1",
            conformanceResult = "SKIPPED",
            walletStatus = "SKIPPED",
            skipReason = "FAPI 2.0 client test: needs wallet-initiated issuance, which the " +
                "harness cannot yet trigger",
        )
        assertTrue(skipped.isAccepted())

        val reportRoot = Files.createTempDirectory("openid-conformance-fapi2-skip").toString()
        ConformanceReportWriter.writeTestPlanResults(
            role = ConformanceReportWriter.Role.VCI_WALLET,
            results = listOf(skipped),
            reportRoot = reportRoot,
            allowFailure = true,
        )
        val summary = Files.readString(
            ConformanceReportWriter.reportDir(ConformanceReportWriter.Role.VCI_WALLET, reportRoot)
                .resolve("summary.md")
        )
        assertTrue(summary.contains("`skipped`"))
        assertTrue(summary.contains("wallet-initiated issuance"))
        assertFalse(summary.contains("`failed`"))
    }

    @Test
    fun allowFailureDefaultsToTrueWhenUnset() {
        // When the env var is not injected in this JVM, unset → allow.
        // CI injects the var (possibly empty); empty is also allow.
        if (System.getenv(ConformanceCiFlags.ALLOW_FAILURE_ENV) == null) {
            assertTrue(ConformanceCiFlags.allowFailure())
            assertFalse(ConformanceCiFlags.strictResults())
        }
    }
}
