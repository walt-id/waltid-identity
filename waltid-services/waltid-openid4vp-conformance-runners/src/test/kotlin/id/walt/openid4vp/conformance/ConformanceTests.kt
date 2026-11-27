package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.config.ConformanceConfig
import id.walt.openid4vp.conformance.report.ConformanceReportWriter
import id.walt.openid4vp.conformance.testplans.ConformanceTestRunner
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.condition.EnabledIf
import kotlin.test.Test

// TODO: Rename to Verifier2ConformanceTests (requires change in CI script)
class ConformanceTests {

    companion object {
        val verifier2UrlPrefix: String =
            System.getenv("VERIFIER_NGROK_URL")?.trimEnd('/')?.plus("/verification-session")
                ?: "https://verifier2.localhost/verification-session"
        val conformanceHost: String = ConformanceConfig.CONFORMANCE_HOST
        val conformancePort: Int = ConformanceConfig.CONFORMANCE_PORT

        val conformanceServerVersionResult = runBlocking {
            runCatching {
                ConformanceInterface(conformanceHost, conformancePort).getServerVersion()
            }.onFailure {
                println("Error getting server version: $it")
            }
        }

        @JvmStatic
        val isConformanceAvailable = conformanceServerVersionResult.isSuccess

        @JvmStatic
        @AfterAll
        fun writeSkippedSummaryIfSuiteUnavailable() {
            if (isConformanceAvailable) return
            ConformanceReportWriter.writeUnavailableIfEmpty(
                role = ConformanceReportWriter.Role.VP_VERIFIER,
                reason = "Conformance suite not available at $conformanceHost:$conformancePort",
            )
        }
    }

    @Test
    @EnabledIf("isConformanceAvailable")
    // No runTest wrapper here: ConformanceTestRunner.run() is blocking and E2ETest.testBlock already
    // establishes its own runTest scope, so nesting one would only add a second, shorter deadline.
    fun runVerifier2ConformanceTests() {
        ConformanceTestRunner(
            verifier2UrlPrefix, conformanceHost, conformancePort
        ).run()
    }

}
