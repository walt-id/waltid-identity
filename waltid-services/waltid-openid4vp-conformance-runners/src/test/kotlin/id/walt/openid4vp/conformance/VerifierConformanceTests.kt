package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.config.ConformanceConfig
import id.walt.openid4vp.conformance.testplans.VerifierConformanceTestRunner
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Timeout
import kotlin.test.Test
import java.util.concurrent.TimeUnit

/**
 * Verifier Conformance Tests
 *
 * Tests OpenID4VP verifier compliance against the OpenID Foundation conformance suite.
 * Includes HAIP (High Assurance Interoperability Profile) test plans for eIDAS 2.0 compliance.
 *
 * Prerequisites:
 * 1. Conformance suite running (local Docker)
 * 2. verifier-api2 running locally on port 7003
 * 3. ngrok tunnel to expose local verifier to conformance suite
 *
 * Setup:
 * ```bash
 * # Terminal 1: Start conformance suite
 * cd ~/dev/openid/conformance-suite
 * docker compose -f docker-compose-walt.yml up -d
 *
 * # Terminal 2: Start verifier-api2
 * cd ~/dev/walt-id/waltid-unified-build/waltid-identity
 * ./gradlew :waltid-services:waltid-verifier-api2:run
 *
 * # Terminal 3: Start ngrok tunnel
 * ngrok http 7003
 * # Copy the HTTPS URL (e.g., https://abc123.ngrok-free.app)
 *
 * # Terminal 4: Run tests
 * export VERIFIER_NGROK_URL="https://abc123.ngrok-free.app"
 * ./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "VerifierConformanceTests"
 * ```
 */
open class VerifierConformanceTests {

    companion object {
        private const val VERIFIER_NGROK_URL_PROPERTY = "verifier.ngrok.url"
        private const val VERIFIER_NGROK_URL_ENV = "VERIFIER_NGROK_URL"

        /**
         * Get verifier ngrok URL from system property or environment variable.
         */
        val verifierNgrokUrl: String? = System.getProperty(VERIFIER_NGROK_URL_PROPERTY)
            ?: System.getenv(VERIFIER_NGROK_URL_ENV)

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
        val isVerifierUrlConfigured = !verifierNgrokUrl.isNullOrBlank()

        init {
            println()
            println("=".repeat(80))
            println("Verifier Conformance Tests")
            println("=".repeat(80))
            println()
            println("Conformance suite: $conformanceHost:$conformancePort")
            println("Conformance available: $isConformanceAvailable")
            println("Verifier ngrok URL: ${verifierNgrokUrl ?: "<not configured>"}")
            println()

            if (!isConformanceAvailable) {
                println("To start conformance suite:")
                println("  cd ~/dev/openid/conformance-suite")
                println("  docker compose -f docker-compose-walt.yml up -d")
                println()
            }

            if (!isVerifierUrlConfigured) {
                println("To configure verifier URL:")
                println("  1. Start verifier-api2:")
                println("     ./gradlew :waltid-services:waltid-verifier-api2:run")
                println()
                println("  2. Start ngrok:")
                println("     ngrok http 7003")
                println()
                println("  3. Set environment variable:")
                println("     export VERIFIER_NGROK_URL=\"https://xxxx.ngrok-free.app\"")
                println()
            }

            println("=".repeat(80))
            println()
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    fun runVerifierConformanceTests() = runBlocking {
        assumeTrue(isConformanceAvailable, "OpenID conformance suite is not reachable at $conformanceHost:$conformancePort")
        assumeTrue(isVerifierUrlConfigured, "VERIFIER_NGROK_URL environment variable not set")

        val runner = VerifierConformanceTestRunner(
            verifierNgrokUrl = requireNotNull(verifierNgrokUrl),
            conformanceHost = conformanceHost,
            conformancePort = conformancePort
        )

        try {
            val results = runner.run()
            
            // Print summary
            println()
            println("=".repeat(80))
            println("VERIFIER CONFORMANCE TEST RESULTS")
            println("=".repeat(80))
            
            val passed = results.count { it.passed }
            val failed = results.count { !it.passed }
            
            results.forEach { result ->
                val status = if (result.passed) "✅ PASS" else "❌ FAIL"
                println("$status: ${result.testName}")
                if (!result.passed && result.message != null) {
                    println("       ${result.message}")
                }
            }
            
            println()
            println("Summary: $passed passed, $failed failed out of ${results.size} tests")
            println("=".repeat(80))
            
            // Fail the test if any test failed
            if (failed > 0) {
                throw AssertionError("$failed verifier conformance test(s) failed")
            }
        } finally {
            runner.close()
        }
    }
}
