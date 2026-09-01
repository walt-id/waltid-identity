package id.walt.openid4vp.conformance

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
            "https://verifier2.localhost/verification-session" // "https://xyz.ngrok-free.app/verification-session"
        val conformanceHost: String = "localhost.emobix.co.uk" // "conformance.waltid.cloud" // conformance-v5-1-43.waltid.cloud
        val conformancePort: Int = 8443 // 443

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
            ConformanceReportWriter.writeSkippedIfEmpty(
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
