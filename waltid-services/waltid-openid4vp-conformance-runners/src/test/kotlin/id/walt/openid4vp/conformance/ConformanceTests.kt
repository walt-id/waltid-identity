package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.testplans.ConformanceTestRunner
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.condition.EnabledIf
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class ConformanceTests {

    companion object {
        val conformanceServerVersionResult = runBlocking { runCatching { ConformanceInterface().getServerVersion() } }

        @JvmStatic
        val isConformanceAvailable = conformanceServerVersionResult.isSuccess
    }

    @Test
    @EnabledIf("isConformanceAvailable")
    fun runConformanceTests() = runTest(timeout = 5.minutes) {
        ConformanceTestRunner().run()
    }

}
