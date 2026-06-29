package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.testplans.ConformanceTestRunner
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.condition.EnabledIf
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

// TODO: Rename to Verifier2ConformanceTests (requires change in CI script)
class ConformanceTests {

    companion object {
        // Conformance suite runs in Docker and needs to reach the host
        // Use the host's actual IP address (not localhost or 127.0.0.1 which are Docker-internal)
        val verifier2UrlPrefix: String =
            "http://10.0.0.79:7003/verification-session"
        val conformanceHost: String = "localhost.emobix.co.uk"
        val conformancePort: Int = 8443

        val conformanceServerVersionResult = runBlocking {
            runCatching {
                ConformanceInterface(conformanceHost, conformancePort).getServerVersion()
            }.onFailure {
                println("Error getting server version: $it")
            }
        }

        @JvmStatic
        val isConformanceAvailable = conformanceServerVersionResult.isSuccess
    }

    @Test
    @EnabledIf("isConformanceAvailable")
    fun runVerifier2ConformanceTests() = runTest(timeout = 5.minutes) {
        ConformanceTestRunner(
            verifier2UrlPrefix, conformanceHost, conformancePort
        ).run()
    }

}
