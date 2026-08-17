package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.report.ConformanceCiFlags
import id.walt.openid4vp.conformance.report.ConformanceReportWriter
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import java.nio.file.Files
import kotlin.test.Test
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
    fun allowFailureDefaultsToTrueWhenUnset() {
        // When the env var is not injected in this JVM, unset → allow.
        // CI injects the var (possibly empty); empty is also allow.
        if (System.getenv(ConformanceCiFlags.ALLOW_FAILURE_ENV) == null) {
            assertTrue(ConformanceCiFlags.allowFailure())
            assertFalse(ConformanceCiFlags.strictResults())
        }
    }
}
