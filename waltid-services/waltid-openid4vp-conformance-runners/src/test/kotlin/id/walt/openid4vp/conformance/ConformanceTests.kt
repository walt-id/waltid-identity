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
        private const val verifier2UrlPrefixProperty = "openid4vp.conformance.verifier2-url-prefix"
        private const val verifier2UrlPrefixEnv = "OPENID4VP_CONFORMANCE_VERIFIER2_URL_PREFIX"

        val verifier2UrlPrefix: String =
            System.getProperty(verifier2UrlPrefixProperty)
                ?: System.getenv(verifier2UrlPrefixEnv)
                ?: "http://host.docker.internal:7003/verification-session"
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
    }

    @Test
    @EnabledIf("isConformanceAvailable")
    fun runVerifier2ConformanceTests() = runTest(timeout = 5.minutes) {
        ConformanceTestRunner(
            verifier2UrlPrefix, conformanceHost, conformancePort
        ).run()
    }

}
