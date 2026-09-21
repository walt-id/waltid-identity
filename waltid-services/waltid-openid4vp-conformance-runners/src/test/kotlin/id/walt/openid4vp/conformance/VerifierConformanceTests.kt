package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.config.ConformanceConfig
import id.walt.openid4vp.conformance.report.ConformanceCiFlags
import id.walt.openid4vp.conformance.report.ConformanceReportWriter
import id.walt.openid4vp.conformance.testplans.VerifierConformanceTestRunner
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

/**
 * Verifier Conformance Tests
 *
 * Tests OpenID4VP verifier compliance against the OpenID Foundation conformance suite.
 * Includes HAIP (High Assurance Interoperability Profile) test plans for eIDAS 2.0 compliance.
 *
 * Prerequisites:
 * 1. Conformance suite running (local Docker) - self-signed cert needs trusting via
 *    CONFORMANCE_EXTRA_CA_PEM; see docs/VP-VERIFIER.md Quick Start for the current recommended setup
 * 2. verifier-api2 running locally - check its startup log for the port it actually binds
 *    (config/web.conf; this has moved before, e.g. 7003 -> 7004)
 * 3. ngrok tunnel on that port, to expose local verifier to conformance suite
 *
 * Setup (see docs/VP-VERIFIER.md for the full walkthrough, including the cert trust step):
 * ```bash
 * # Terminal 1: Start the conformance suite (upstream checkout, recommended)
 * cd ~/dev/openid/conformance-suite
 * docker compose -f docker-compose-prebuilt.yml up -d
 *
 * # Terminal 2: Start verifier-api2
 * cd ~/dev/walt-id/waltid-unified-build
 * ./gradlew :waltid-services:waltid-verifier-api2:run
 *
 * # Terminal 3: Start ngrok tunnel on the port verifier-api2 logged
 * ngrok http <port>
 * # Copy the HTTPS URL (e.g., https://abc123.ngrok-free.app)
 *
 * # Terminal 4: Run tests
 * export VERIFIER_NGROK_URL="https://abc123.ngrok-free.app"
 * export CONFORMANCE_EXTRA_CA_PEM=/path/to/extracted/suite/cert.pem
 * ./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "VerifierConformanceTests" --rerun
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
                println("To start the conformance suite, and to trust its self-signed cert via")
                println("CONFORMANCE_EXTRA_CA_PEM, see docs/VP-VERIFIER.md Quick Start.")
                println("  cd ~/dev/openid/conformance-suite")
                println("  docker compose -f docker-compose-prebuilt.yml up -d")
                println()
            }

            if (!isVerifierUrlConfigured) {
                println("To configure verifier URL:")
                println("  1. Start verifier-api2 and check its startup log for the port it bound:")
                println("     ./gradlew :waltid-services:waltid-verifier-api2:run")
                println()
                println("  2. Start ngrok on that port:")
                println("     ngrok http <port>")
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
    fun runVerifierConformanceTests() {
        assumeTrue(isConformanceAvailable, "OpenID conformance suite is not reachable at $conformanceHost:$conformancePort")
        assumeTrue(isVerifierUrlConfigured, "VERIFIER_NGROK_URL environment variable not set")

        // Plain runBlocking, not runTest: this body does real network I/O against Docker/ngrok, and
        // runTest's virtual-time TestDispatcher does not reliably wait for real async completions
        // arriving off its dispatcher - it was observed to hang every second real request until the
        // 60s HttpTimeout killer fired, even though the server had already answered. See the sibling
        // IssuerConformanceTests, which uses the same runBlocking + withTimeout pattern for the same reason.
        runBlocking {
            withTimeout(10.minutes) {
                val runner = VerifierConformanceTestRunner(
                    verifierNgrokUrl = requireNotNull(verifierNgrokUrl),
                    conformanceHost = conformanceHost,
                    conformancePort = conformancePort
                )

                try {
                    val results = runner.run()

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

                    ConformanceReportWriter.writeTestPlanResults(
                        role = ConformanceReportWriter.Role.VP_VERIFIER,
                        results = results,
                        conformanceHost = conformanceHost,
                        conformancePort = conformancePort,
                    )
                    ConformanceReportWriter.failIfNeededFromTestPlanResults(
                        role = ConformanceReportWriter.Role.VP_VERIFIER,
                        results = results,
                        allowFailure = ConformanceCiFlags.allowFailure(),
                    )
                } finally {
                    runner.close()
                }
            }
        }
    }
}
