package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.testplans.ConformanceTestRunner
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.condition.EnabledIf
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

@Disabled
class ConformanceTests {

    companion object {
        val verifier2UrlPrefix: String = "https://verifier2.localhost/verification-session" // "https://xyz.ngrok-free.app/verification-session"
        val conformanceHost: String = "conformance.waltid.cloud" // "localhost.emobix.co.uk"
        val conformancePort: Int = 443 // 8443

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
    fun runConformanceTests() = runTest(timeout = 5.minutes) {
        ConformanceTestRunner(
            verifier2UrlPrefix, conformanceHost, conformancePort
        ).run()
    }

}
