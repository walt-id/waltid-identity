package id.walt.openid4vp.conformance.testplans.runner

import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import id.walt.openid4vp.conformance.testplans.plans.vci.wallet.VciWalletTestPlan
import io.ktor.client.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Executes VCI wallet conformance test plans through the local wallet adapter.
 *
 * ## Flow
 *
 * 1. Create test plan on conformance suite (suite acts as issuer)
 * 2. Get list of test modules
 * 3. For each module:
 *    a. Start module (conformance suite calls the adapter credential-offer endpoint)
 *    b. Open the adapter's offer URL in a browser when authorization is needed
 *    c. Start issuance from the adapter page
 *    d. Adapter completes the OAuth callback and credential fetch
 *    e. Poll for result
 * 4. Collect and return results
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
        val modules = createResponse.modules
        println("Test modules: ${modules.size}")
        modules.forEach { println("   - ${it.testModule}") }
        println()

        val results = mutableListOf<TestPlanResult>()
        val modulesToRun = if (testPlan.isHaip) {
            modules
        } else {
            // Non-HAIP profiles still keep the reduced execution path until the
            // multi-module wallet flow is hardened for the reference profiles too.
            modules.take(1)
        }

        modulesToRun.forEachIndexed { index, module ->
            println("[${index + 1}/${modules.size}] Running: ${module.testModule}")
            val result = runModule(testPlanId, module)
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

    private suspend fun runModule(
        testPlanId: String,
        module: id.walt.openid4vp.conformance.testplans.httpdata.CreateTestPlanResponse.Module
    ): TestPlanResult {
        // Start test with variant
        val variantJson = module.variant.takeIf { it.isNotEmpty() } ?: JsonObject(emptyMap())

        val createTestUrl = conformance.buildCreateTestUrl(testPlanId, module.testModule, variantJson)
        val createTestResponse = conformance.createTest(createTestUrl)
        val testId = createTestResponse.id

        println("   Test ID: $testId")
        println("   View: https://$conformanceHost:$conformancePort/log-detail.html?log=$testId")
        println("   Credential Offer Endpoint: $walletAdapterUrl/credential-offer")
        println("   Note: Open the credential offer URL in your browser to start issuance")
        println()

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

            if (testInfo.status == "WAITING" && attempts % 20 == 0) {
                println("   Status: WAITING (open the adapter offer URL to continue)")
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
