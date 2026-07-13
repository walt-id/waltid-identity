package id.walt.openid4vp.conformance.testplans.runner

import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.httpdata.CreateTestPlanResponse
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import id.walt.openid4vp.conformance.testplans.plans.vp.wallet.WalletTestPlan
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.Json

/**
 * Executes a single wallet conformance test plan
 * 
 * Flow:
 * 1. Create test plan on conformance suite
 * 2. Get list of test modules from create response
 * 3. For each module:
 *    a. Create test instance (via /api/runner)
 *    b. Wait for WAITING state
 *    c. Trigger wallet to process authorization request
 *    d. Wait for completion
 *    e. Get result
 * 4. Collect and return results
 */
class WalletTestPlanRunner(
    val testPlan: WalletTestPlan,
    val conformanceHttp: HttpClient,
    val conformanceHost: String,
    val conformancePort: Int
) {

    private val conformance = ConformanceInterface(conformanceHost, conformancePort)

    /**
     * Execute the test plan and return results
     */
    suspend fun test(): List<TestPlanResult> {
        println()
        println("=" .repeat(80))
        println("Test Plan: ${testPlan.description}")
        println("=" .repeat(80))
        println("  Plan name: ${testPlan.planName}")
        println("  Variant: ${testPlan.variant}")
        println("  HAIP: ${testPlan.isHAIP}")
        println("  Encrypted response: ${testPlan.requiresEncryptedResponse}")
        println("  Signed request: ${testPlan.requiresSignedRequest}")
        println()

        // Create test plan (response includes modules)
        val planResponse = createTestPlan()
        val testPlanId = planResponse.id
        println("Test plan created: $testPlanId")
        println("View plan: https://$conformanceHost:$conformancePort/plan-detail.html?plan=$testPlanId")

        // Get test modules from create response
        val modules = planResponse.modules
        println("Test modules: ${modules.size}")
        modules.forEach { println("   - ${it.testModule}") }
        println()

        // Run each module
        val results = mutableListOf<TestPlanResult>()
        modules.forEachIndexed { index, module ->
            println("[${index + 1}/${modules.size}] Running module: ${module.testModule}")
            
            val result = runModule(testPlanId, module)
            results.add(result)

            println("   Result: ${result.walletStatus}")
            if (result.errorMessage != null) {
                println("   Error: ${result.errorMessage}")
            }
            println()
        }

        // Print summary
        printSummary(results)

        return results
    }

    /**
     * Create test plan on conformance suite.
     * Returns the full response which includes the modules list.
     */
    private suspend fun createTestPlan(): CreateTestPlanResponse {        
        val variantJson = Json.encodeToString(testPlan.variant)
        
        println("DEBUG: Creating test plan...")
        println("DEBUG: Plan name: ${testPlan.planName}")
        println("DEBUG: Variant JSON: $variantJson")
        println("DEBUG: Configuration: ${testPlan.configuration}")
        
        val createTestPlanUrl = conformance.createTestPlanUrlWithConfig {
            append("planName", testPlan.planName)
            append("variant", variantJson)
        }
        
        println("DEBUG: URL: $createTestPlanUrl")
        
        // Send configuration directly - conformance suite expects client.jwks at root level
        val response = conformance.createTestPlan(createTestPlanUrl, testPlan.configuration)

        println("Created test plan: ${response.id}")
        return response
    }

    /**
     * Run a single test module.
     * Uses the same API pattern as verifier tests: buildCreateTestUrl + createTest.
     */
    private suspend fun runModule(testPlanId: String, module: CreateTestPlanResponse.Module): TestPlanResult {
        val moduleId = module.testModule
        
        try {
            // Create test instance for this module (same API as verifier tests)
            val createTestUrl = conformance.buildCreateTestUrl(testPlanId, module.testModule, module.variant)
            println("   Creating test: $createTestUrl")
            
            val createTestResponse = conformance.createTest(createTestUrl)
            val testId = createTestResponse.id
            println("   Test ID: $testId")
            println("   View: https://$conformanceHost:$conformancePort/log-detail.html?log=$testId")

            // Wait for test to be ready (WAITING state)
            conformance.waitForTestStatus(testId, shouldBeWaiting = true)

            // Get the test run result which contains the wallet authorization URL
            val testRunResult = conformance.getTestRun(testId)
            
            // For wallet tests, the conformance suite provides a URL in browser.urls
            // that should be called to trigger the wallet (simulating QR code scan / deep link)
            val walletAuthUrl = testRunResult.getWalletAuthorizationUrl()
                ?: throw IllegalStateException("No wallet authorization URL in browser.urls")
            println("   Wallet authorization URL: $walletAuthUrl")
            
            // Call the wallet adapter to trigger the authorization flow
            // The URL from conformance suite points to ngrok/docker URL, but we call localhost directly
            val localWalletUrl = walletAuthUrl
                .replace(Regex("https?://[^/]+"), "http://127.0.0.1:7006")
            println("   Calling local adapter: $localWalletUrl")
            val walletResponse = conformanceHttp.get(localWalletUrl)
            println("   Wallet adapter response: ${walletResponse.status}")

            // Wait for test to complete (no longer WAITING)
            conformance.waitForTestStatus(testId, shouldBeWaiting = false)

            // Get final result from test info
            val testInfo = conformance.getTestRunInfo(testId)
            val conformanceResult = testInfo.result ?: "UNKNOWN"
            
            // For wallet tests, wallet status == conformance result
            val walletStatus = when {
                testPlan.expectRejection && conformanceResult == "PASSED" -> "REJECTED"
                conformanceResult == "PASSED" -> "PASSED"
                conformanceResult == "FAILED" -> "FAILED"
                conformanceResult == "WARNING" -> "PASSED" // Warnings are acceptable
                else -> "UNKNOWN"
            }

            return TestPlanResult(
                conformanceTestId = testId,
                conformanceResult = conformanceResult,
                walletStatus = walletStatus,
                errorMessage = null
            )
            
        } catch (e: Exception) {
            return TestPlanResult(
                conformanceTestId = moduleId,
                conformanceResult = "ERROR",
                walletStatus = "ERROR",
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * Print test results summary
     */
    private fun printSummary(results: List<TestPlanResult>) {
        println()
        println("Test Results:")
        println("-" .repeat(80))
        println("  Total modules: ${results.size}")

        val passed = results.count { it.walletStatus == "PASSED" }
        val failed = results.count { it.walletStatus == "FAILED" }
        val rejected = results.count { it.walletStatus == "REJECTED" }
        val errors = results.count { it.walletStatus == "ERROR" }
        val timeouts = results.count { it.walletStatus == "TIMEOUT" }

        println("  Passed:  $passed")
        if (failed > 0) println("  Failed:  $failed")
        if (rejected > 0) println("  Rejected: $rejected")
        if (errors > 0) println("  Errors:  $errors")
        if (timeouts > 0) println("  Timeouts: $timeouts")
        println()

        results.forEachIndexed { i, result ->
            val icon = when (result.walletStatus) {
                "PASSED" -> "[PASS]"
                "FAILED" -> "[FAIL]"
                "REJECTED" -> "[RJCT]"
                "ERROR" -> "[ERR ]"
                "TIMEOUT" -> "[TIME]"
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

        // Assert expectations
        if (!testPlan.optional) {
            if (testPlan.expectRejection) {
                val allRejected = results.all { it.walletStatus == "REJECTED" || it.conformanceResult == "PASSED" }
                check(allRejected) {
                    "Negative test plan expected all requests to be rejected by wallet, but some were accepted"
                }
            } else {
                val allPassed = results.all { it.walletStatus == "PASSED" }
                check(allPassed) {
                    "Test plan had ${results.count { it.walletStatus != "PASSED" }} failures"
                }
            }
        }
    }
}
