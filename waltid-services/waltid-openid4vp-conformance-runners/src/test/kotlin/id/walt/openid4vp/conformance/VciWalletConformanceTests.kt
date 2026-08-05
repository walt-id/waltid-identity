package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.testplans.WalletConformanceRuntimeConfig
import id.walt.openid4vp.conformance.testplans.WalletConformanceTestRunner
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import kotlin.time.Duration.Companion.minutes

/**
 * OpenID4VCI wallet conformance matrix entry point.
 *
 * The suite is the credential issuer and authorization server; Wallet API2 is
 * the system under test. Select the suite-defined basic or HAIP contexts with
 * OPENID4VCI_WALLET_CONFORMANCE_* environment variables.
 */
class VciWalletConformanceTests {

    @Test
    @EnabledIfSystemProperty(
        named = "openid4vci.conformance.wallet.enabled",
        matches = "true",
    )
    fun runWalletConformanceTests() {
        runBlocking {
            val runtime = WalletConformanceRuntimeConfig.fromEnvironment()
            val timeoutMinutes = System.getenv("OPENID4VCI_WALLET_CONFORMANCE_TIMEOUT_MINUTES")
                ?.toLongOrNull()
                ?.coerceAtLeast(1)
                ?: 1_440L

            withTimeout(timeoutMinutes.minutes) {
                WalletConformanceTestRunner(runtime = runtime).run()
            }
        }
    }
}
