package id.walt.openid4vp.conformance.testplans.runner

import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import id.walt.openid4vp.conformance.testplans.plans.vci.wallet.VciWalletTestPlan
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

/**
 * Executes VCI wallet conformance test plans.
 *
 * ## Flow
 *
 * 1. Create test plan on conformance suite (suite acts as issuer)
 * 2. Get list of test modules
 * 3. For each module:
 *    a. Start module (conformance suite provides credential offer)
 *    b. Poll for result (wallet processes offer via adapter)
 * 4. Collect and return results
 *
 * ## Authorization Code Flow
 *
 * For `authorization_code` grant type, the flow requires manual browser interaction:
 * - Test enters WAITING status
 * - User completes OAuth login in browser
 * - Test continues automatically after authorization
 *
 * @param testPlan Test plan configuration
 * @param conformanceHost Conformance suite hostname
 * @param conformancePort Conformance suite port
 * @param walletHttpClient HTTP client for wallet adapter calls
 * @param walletAdapterUrl Wallet adapter base URL
 */
class VciWalletTestPlanRunner(
    val testPlan: VciWalletTestPlan,
    val conformanceHost: String,
    val conformancePort: Int,
    val walletHttpClient: HttpClient,
    val walletAdapterUrl: String = "http://127.0.0.1:7007"
) {

    private val conformance = ConformanceInterface(conformanceHost, conformancePort)

    /**
     * Execute the test plan and return results.
     */
    suspend fun test(): List<TestPlanResult> {
        printHeader()

        // Create test plan
        val createResponse = createTestPlan()
        val testPlanId = createResponse.id
        println("Test plan created: $testPlanId")

        // Get modules
        val modules = createResponse.modules.map { it.testModule }
        println("Test modules: ${modules.size}")
        modules.forEach { println("   - $it") }
        println()

        // Run modules (limit to 1 for now to avoid alias conflicts)
        val results = mutableListOf<TestPlanResult>()
        val modulesToRun = modules.take(1)  // TODO: Remove limit once auth flow works

        modulesToRun.forEachIndexed { index, moduleId ->
            println("[${index + 1}/${modules.size}] Running: $moduleId")
            val result = runModule(testPlanId, moduleId)
            results.add(result)
            println("   Status: ${result.conformanceResult}")
            if (result.errorMessage != null) {
                println("   Error: ${result.errorMessage}")
            }
            println()
        }

        printSummary(results)
        return results
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Test Execution
    // ─────────────────────────────────────────────────────────────────────────────

    private suspend fun createTestPlan(): id.walt.openid4vp.conformance.testplans.httpdata.CreateTestPlanResponse {
        val variantJson = Json.encodeToString(testPlan.variant)

        println("Creating test plan...")
        println("  Plan: ${testPlan.planName}")
        println("  Variant: $variantJson")

        val url = conformance.createTestPlanUrlWithConfig {
            append("planName", testPlan.planName)
            append("variant", variantJson)
        }

        return conformance.createTestPlan(url, testPlan.configuration)
    }

    private suspend fun runModule(testPlanId: String, moduleId: String): TestPlanResult {
        // Start test
        val createTestUrl = conformance.buildCreateTestUrl(testPlanId, moduleId)
        val createTestResponse = conformance.createTest(createTestUrl)
        val testId = createTestResponse.id

        println("   Test ID: $testId")
        println("   View: https://$conformanceHost:$conformancePort/log-detail.html?log=$testId")

        // Note: For issuer_initiated flow, conformance suite POSTs credential offer to adapter
        println("   Note: issuer_initiated - conformance suite sends offer to adapter")

        // Poll for result (5 minute timeout for auth code flow)
        val maxAttempts = 600  // 600 * 500ms = 5 minutes
        var attempts = 0

        while (attempts < maxAttempts) {
            delay(500)
            attempts++

            val testInfo = conformance.getTestRunInfo(testId)

            if (testInfo.status in setOf("FINISHED", "INTERRUPTED")) {
                return TestPlanResult(
                    conformanceTestId = testId,
                    conformanceResult = testInfo.result ?: "UNKNOWN",
                    walletStatus = testInfo.result ?: "UNKNOWN",
                    errorMessage = if (testInfo.result != "PASSED") {
                        "Test finished: ${testInfo.result}"
                    } else null
                )
            }

            // Log WAITING status periodically
            if (testInfo.status == "WAITING" && attempts % 20 == 0) {
                println("   Status: WAITING (may require manual OAuth login)")
            }
        }

        return TestPlanResult(
            conformanceTestId = testId,
            conformanceResult = "TIMEOUT",
            walletStatus = "TIMEOUT",
            errorMessage = "Module did not complete within 5 minutes"
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Output Formatting
    // ─────────────────────────────────────────────────────────────────────────────

    private fun printHeader() {
        println()
        println("═".repeat(80))
        println(" VCI Wallet Test Plan: ${testPlan.description}")
        println("═".repeat(80))
        println("  Format: ${testPlan.credentialFormat}")
        println("  Grant: ${testPlan.grantType}")
        println("  Sender: ${testPlan.senderConstraint}")
        println("  Client Auth: ${testPlan.clientAuthType}")
        println()
    }

    private fun printSummary(results: List<TestPlanResult>) {
        println()
        println("═".repeat(80))
        println(" Results")
        println("═".repeat(80))

        val passed = results.count { it.conformanceResult == "PASSED" }
        val failed = results.count { it.conformanceResult == "FAILED" }
        val errors = results.count { it.conformanceResult == "ERROR" }
        val timeouts = results.count { it.conformanceResult == "TIMEOUT" }

        println("  Total: ${results.size}")
        println("  Passed: $passed")
        if (failed > 0) println("  Failed: $failed")
        if (errors > 0) println("  Errors: $errors")
        if (timeouts > 0) println("  Timeouts: $timeouts")
        println()

        results.forEachIndexed { i, result ->
            val icon = when (result.conformanceResult) {
                "PASSED" -> "✓"
                "FAILED" -> "✗"
                "ERROR" -> "!"
                "TIMEOUT" -> "⏱"
                else -> "?"
            }
            println("  [$i] $icon ${result.conformanceTestId}: ${result.conformanceResult}")
            result.errorMessage?.let { println("       $it") }
        }

        println("═".repeat(80))
    }
}
