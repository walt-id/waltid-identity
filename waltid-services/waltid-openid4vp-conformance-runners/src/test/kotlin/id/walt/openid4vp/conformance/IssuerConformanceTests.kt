package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.ConformanceTests.Companion.conformanceHost
import id.walt.openid4vp.conformance.ConformanceTests.Companion.conformancePort
import id.walt.openid4vp.conformance.testplans.IssuerConformanceTestRunner
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIf

class IssuerConformanceTests {

    companion object {
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
    fun runIssuerConformanceTests() {
        IssuerConformanceTestRunner().run()
    }
}
