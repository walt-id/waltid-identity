package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.config.ConformanceConfig
import id.walt.openid4vp.conformance.testplans.ConformanceTestRunner
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.condition.EnabledIf
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

/**
 * Verifier2 Conformance Tests
 *
 * Tests OpenID4VP verifier compliance against the OpenID Foundation conformance suite.
 *
 * Prerequisites:
 * 1. Conformance suite running (local Docker or cloud)
 * 2. ngrok tunnel to expose local verifier to conformance suite
 *
 * Setup:
 * ```bash
 * # Start conformance suite
 * cd ~/dev/openid/conformance-suite
 * docker compose -f docker-compose-walt.yml up -d
 *
 * # Start ngrok tunnel
 * ngrok http 7003
 *
 * # Copy ngrok URL and set VERIFIER_NGROK_URL environment variable
 * export VERIFIER_NGROK_URL="https://xxxx.ngrok-free.app"
 * ```
 *
 * Run:
 * ```bash
 * ./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "Verifier2ConformanceTests"
 * ```
 */
open class Verifier2ConformanceTests {

    companion object {
        /**
         * Verifier URL prefix - must be accessible from conformance suite Docker container.
         * Set via VERIFIER_NGROK_URL environment variable or edit directly for testing.
         */
        val verifierUrlPrefix: String = System.getenv("VERIFIER_NGROK_URL")
            ?.let { "$it/verification-session" }
            ?: ConformanceConfig.VERIFIER_URL_PREFIX_PLACEHOLDER

        val conformanceHost: String = ConformanceConfig.CONFORMANCE_HOST
        val conformancePort: Int = ConformanceConfig.CONFORMANCE_PORT

        val conformanceServerVersionResult = runBlocking {
            runCatching {
                ConformanceInterface(conformanceHost, conformancePort).getServerVersion()
            }.onFailure {
                println("Conformance suite not available at $conformanceHost:$conformancePort")
                println("Error: $it")
            }
        }

        @JvmStatic
        val isConformanceAvailable = conformanceServerVersionResult.isSuccess

        @JvmStatic
        val isVerifierUrlConfigured = !ConformanceConfig.isPlaceholderUrl(verifierUrlPrefix)

        @JvmStatic
        fun canRunTests(): Boolean = isConformanceAvailable && isVerifierUrlConfigured

        init {
            println()
            println("=" .repeat(80))
            println("Verifier2 Conformance Tests")
            println("=" .repeat(80))
            println()
            println("Conformance suite: $conformanceHost:$conformancePort")
            println("Conformance available: $isConformanceAvailable")
            println("Verifier URL: $verifierUrlPrefix")
            println("Verifier URL configured: $isVerifierUrlConfigured")
            println()

            if (!isConformanceAvailable) {
                println("To start conformance suite:")
                println("  cd ~/dev/openid/conformance-suite")
                println("  docker compose -f docker-compose-walt.yml up -d")
                println()
            }

            if (!isVerifierUrlConfigured) {
                println("To configure verifier URL:")
                println("  1. Start ngrok: ngrok http 7003")
                println("  2. Set environment variable:")
                println("     export VERIFIER_NGROK_URL=\"https://xxxx.ngrok-free.app\"")
                println("  3. Or edit verifierUrlPrefix in this file")
                println()
            }

            println("=" .repeat(80))
            println()
        }
    }

    @Test
    @EnabledIf("canRunTests")
    fun runVerifier2ConformanceTests() = runTest(timeout = 5.minutes) {
        ConformanceTestRunner(
            verifierUrlPrefix, conformanceHost, conformancePort
        ).run()
    }
}
