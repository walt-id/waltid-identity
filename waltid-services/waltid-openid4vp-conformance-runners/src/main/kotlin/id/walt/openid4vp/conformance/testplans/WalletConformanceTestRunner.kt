@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.openid4vp.conformance.testplans

import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.runner.WalletTestPlanRunner
import id.walt.openid4vp.conformance.testplans.wallet.WalletTestPlan
import io.ktor.client.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlin.test.assertNotNull

/**
 * Wallet Conformance Test Runner
 * 
 * Runs OpenID4VP conformance tests from the wallet side (presentation).
 * The conformance suite acts as the verifier, and this runner operates
 * a local wallet instance to respond to presentation requests.
 * 
 * Architecture:
 * ```
 * [Conformance Suite]  →  Authorization Request  →  [Local Wallet]
 *      (Verifier)      ←   VP Response (JWE)     ←   (Presenter)
 * ```
 * 
 * Flow:
 * 1. Test runner creates test plan on conformance suite
 * 2. Conformance suite generates authorization request
 * 3. Local wallet processes request (authenticate, select credentials)
 * 4. Wallet generates encrypted VP response
 * 5. Conformance suite validates response
 * 6. Test runner collects results
 * 
 * HAIP Mode:
 * When `haipMode = true`, enforces HAIP requirements:
 * - Signed request validation (MANDATORY)
 * - Encrypted response (direct_post.jwt) (MANDATORY)
 * - P-256 key curve enforcement (MANDATORY)
 * - SHA-256 hash algorithm (MANDATORY)
 * - Holder binding validation (MANDATORY)
 * 
 * @param walletApiUrl Base URL of local wallet-api2 instance (default: http://localhost:7002)
 * @param conformanceHost Conformance suite hostname (default: localhost.emobix.co.uk)
 * @param conformancePort Conformance suite port (default: 8443)
 * @param planName OpenID4VP test plan name (e.g., "oid4vp-1final-wallet-haip-test-plan")
 * @param variant Test plan variant (credential_format, client_id_prefix, etc.)
 * @param haipMode Enable HAIP compliance mode (default: false)
 * @param expectRejection For negative tests - expect wallet to reject request (default: false)
 * @param optional Mark test as optional (may skip if not supported) (default: false)
 */
class WalletConformanceTestRunner(
    val walletApiUrl: String = "http://localhost:7002",
    val conformanceHost: String = "localhost.emobix.co.uk",
    val conformancePort: Int = 8443,
    val planName: String,
    val variant: Map<String, String>,
    val haipMode: Boolean = false,
    val expectRejection: Boolean = false,
    val optional: Boolean = false
) {

    /**
     * Build test plan configuration for wallet-side testing
     */
    private fun buildTestPlan(): WalletTestPlan {
        return WalletTestPlan(
            planName = planName,
            variant = variant,
            walletApiUrl = walletApiUrl,
            conformanceHost = conformanceHost,
            conformancePort = conformancePort,
            haipMode = haipMode,
            expectRejection = expectRejection
        )
    }

    /**
     * Run the conformance test
     * 
     * Steps:
     * 1. Start local wallet-api2 instance
     * 2. Check conformance suite availability
     * 3. Create test plan on conformance suite
     * 4. Run test modules (conformance suite → wallet → conformance suite)
     * 5. Collect and report results
     * 
     * @throws AssertionError if conformance suite not available
     * @throws Exception if test plan execution fails
     */
    suspend fun run(httpClient: HttpClient): List<id.walt.openid4vp.conformance.testplans.plans.TestPlanResult> {
        val conformance = ConformanceInterface(conformanceHost, conformancePort)

        // Check conformance suite availability
        val conformanceVersion = conformance.getServerVersion()
        assertNotNull(conformanceVersion, "Conformance suite not available")
        println("Conformance server version $conformanceVersion available")

        val testPlan = buildTestPlan()
        val planDescription = buildString {
            append(testPlan.planName)
            append(" [")
            append(variant.entries.joinToString(", ") { "${it.key}=${it.value}" })
            append("]")
            if (haipMode) append(" (HAIP)")
            if (expectRejection) append(" (Negative)")
            if (optional) append(" (Optional)")
        }

        println()
        println("=" .repeat(80))
        println("Test Plan: $planDescription")
        println("=" .repeat(80))

        val runner = WalletTestPlanRunner(
            testPlan = testPlan,
            httpClient = httpClient,
            conformanceHost = conformanceHost,
            conformancePort = conformancePort
        )

        val results = runner.test()

        println()
        println("Test Results:")
        println("-" .repeat(80))
        println("  Total modules: ${results.size}")

        val passed = results.count { it.walletStatus == "PASSED" }
        val failed = results.count { it.walletStatus == "FAILED" }
        val skipped = results.count { it.walletStatus == "SKIPPED" }

        println("  Passed:  $passed")
        if (failed > 0) println("  Failed:  $failed")
        if (skipped > 0) println("  Skipped: $skipped")
        println()

        results.forEachIndexed { i, result ->
            val icon = when (result.walletStatus) {
                "PASSED" -> "[PASS]"
                "FAILED" -> "[FAIL]"
                "SKIPPED" -> "[SKIP]"
                else -> "[????]"
            }
            println("  [$i] $icon ${result.conformanceTestId}")
            println("       Conformance: ${result.conformanceResult}")
            println("       Wallet:      ${result.walletStatus}")
            if (result.errorMessage != null) {
                println("       Error:       ${result.errorMessage}")
            }
        }

        println("=" .repeat(80))
        println()

        // For non-optional tests, assert all passed
        if (!optional && !expectRejection) {
            val allPassed = results.all { it.walletStatus == "PASSED" }
            check(allPassed) {
                "Test plan $planDescription had ${results.count { it.walletStatus == "FAILED" }} failures"
            }
        }

        // For negative tests, assert wallet rejected appropriately
        if (expectRejection) {
            val allRejected = results.all { 
                it.walletStatus == "REJECTED" || it.conformanceResult == "PASSED"
            }
            check(allRejected) {
                "Negative test plan $planDescription expected all requests to be rejected by wallet"
            }
        }

        return results
    }
}


