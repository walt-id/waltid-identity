package id.walt.openid4vp.conformance.testplans.runner

import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.httpdata.CreateTestPlanResponse
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import id.walt.openid4vp.conformance.testplans.plans.vp.wallet.WalletTestPlan
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*

/**
 * Executes a single wallet conformance test plan
 * 
 * Flow:
 * 1. Create test plan on conformance suite
 * 2. Get list of test modules
 * 3. For each module:
 *    a. Start module (conformance suite generates request)
 *    b. Wallet processes request (automatic callback)
 *    c. Poll for result (wait up to 30s)
 * 4. Collect and return results
 */
class WalletTestPlanRunner(
    val testPlan: WalletTestPlan,
    val httpClient: HttpClient,
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

        // Get test modules from create response
        val modules = planResponse.modules.map { it.testModule }
        println("Test modules: ${modules.size}")
        modules.forEach { println("   - $it") }
        println()

        // Run each module
        val results = mutableListOf<TestPlanResult>()
        modules.forEachIndexed { index, moduleId ->
            println("[${index + 1}/${modules.size}] Running module: $moduleId")
            
            val result = runModule(testPlanId, moduleId)
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
        // Conformance suite API expects planName AND variant as URL-encoded query parameters
        val variantJson = Json.encodeToString(testPlan.variant)
        
        println("DEBUG: Creating test plan...")
        println("DEBUG: Plan name: ${testPlan.planName}")
        println("DEBUG: Variant JSON: $variantJson")
        println("DEBUG: Configuration: ${testPlan.configuration}")
        
        // Build URL using same helper as working verifier tests
        val createTestPlanUrl = conformance.createTestPlanUrlWithConfig {
            append("planName", testPlan.planName)
            append("variant", variantJson)
        }
        
        println("DEBUG: URL: $createTestPlanUrl")
        println("DEBUG: About to make HTTP POST request...")
        
        // Use same createTestPlan method as working verifier tests
        val body = buildJsonObject {
            put("configuration", testPlan.configuration)
        }
        val response = conformance.createTestPlan(createTestPlanUrl, body)

        println("Created test plan: ${response.id}")
        return response
    }

    /**
     * Run a single test module
     */
    private suspend fun runModule(testPlanId: String, moduleId: String): TestPlanResult {
        // Start module
        val startResponse = conformance.conformanceHttp.post("/api/plan/$testPlanId/module/$moduleId/start") {
            contentType(ContentType.Application.Json)
        }

        if (!startResponse.status.isSuccess()) {
            return TestPlanResult(
                conformanceTestId = moduleId,
                conformanceResult = "ERROR",
                walletStatus = "ERROR",
                errorMessage = "Failed to start module: ${startResponse.status}"
            )
        }

        // Poll for result (up to 30 seconds)
        val maxAttempts = 60 // 60 * 500ms = 30 seconds
        var attempts = 0

        while (attempts < maxAttempts) {
            delay(500)
            attempts++

            val resultResponse = conformance.conformanceHttp.get("/api/plan/$testPlanId/module/$moduleId/result")

            if (resultResponse.status.isSuccess()) {
                val resultBody = resultResponse.body<JsonObject>()
                val status = resultBody["status"]?.jsonPrimitive?.content

                if (status == "FINISHED" || status == "FAILED") {
                    val conformanceResult = resultBody["result"]?.jsonPrimitive?.content ?: "UNKNOWN"
                    
                    // For wallet tests, wallet status == conformance result
                    // (conformance suite validates wallet responses)
                    val walletStatus = when {
                        testPlan.expectRejection && conformanceResult == "PASSED" -> "REJECTED" // Wallet correctly rejected
                        conformanceResult == "PASSED" -> "PASSED"
                        conformanceResult == "FAILED" -> "FAILED"
                        else -> "UNKNOWN"
                    }

                    return TestPlanResult(
                        conformanceTestId = moduleId,
                        conformanceResult = conformanceResult,
                        walletStatus = walletStatus,
                        errorMessage = if (conformanceResult == "FAILED") {
                            resultBody["error"]?.jsonPrimitive?.content
                        } else null
                    )
                }
            }
        }

        // Timeout
        return TestPlanResult(
            conformanceTestId = moduleId,
            conformanceResult = "TIMEOUT",
            walletStatus = "TIMEOUT",
            errorMessage = "Module did not complete within 30 seconds"
        )
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
                    "Test plan had ${results.count { it.walletStatus == "FAILED" }} failures"
                }
            }
        }
    }
}
