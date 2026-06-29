@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.openid4vp.conformance.testplans.runner

import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import id.walt.openid4vp.conformance.testplans.wallet.WalletTestPlan
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*

/**
 * Wallet Test Plan Runner
 * 
 * Executes a wallet-side OpenID4VP conformance test plan.
 * 
 * Flow:
 * 1. Create test plan on conformance suite
 * 2. Configure wallet endpoint URL
 * 3. For each test module:
 *    a. Conformance suite generates authorization request
 *    b. Wallet receives request (via HTTP callback)
 *    c. Wallet authenticates request (if signed)
 *    d. Wallet selects credentials matching DCQL query
 *    e. Wallet generates VP response (encrypted if HAIP)
 *    f. Wallet sends response to conformance suite
 *    g. Conformance suite validates response
 * 4. Collect test results
 * 
 * @param testPlan Wallet test plan configuration
 * @param httpClient HTTP client for API calls
 * @param conformanceHost Conformance suite hostname
 * @param conformancePort Conformance suite port
 */
class WalletTestPlanRunner(
    private val testPlan: WalletTestPlan,
    private val httpClient: HttpClient,
    private val conformanceHost: String,
    private val conformancePort: Int
) {
    private val conformance = ConformanceInterface(conformanceHost, conformancePort)

    /**
     * Execute the test plan
     * 
     * @return List of test results (one per module)
     */
    suspend fun test(): List<TestPlanResult> {
        println("Creating wallet test plan on conformance suite...")
        
        // 1. Create test plan on conformance suite
        val testPlanId = createTestPlan()
        println("✅ Test plan created: $testPlanId")

        // 2. Get list of test modules in this plan
        val modules = getTestModules(testPlanId)
        println("📋 Test modules: ${modules.size}")
        modules.forEach { module ->
            println("   - $module")
        }

        // 3. Run each test module
        val results = mutableListOf<TestPlanResult>()
        
        for ((index, moduleId) in modules.withIndex()) {
            println()
            println("[${ index + 1}/${modules.size}] Running module: $moduleId")
            
            val result = runTestModule(testPlanId, moduleId)
            results.add(result)
            
            val icon = when (result.walletStatus) {
                "PASSED" -> "✅"
                "FAILED" -> "❌"
                "SKIPPED" -> "⏭️"
                else -> "❓"
            }
            println("   $icon Result: ${result.walletStatus}")
            
            if (result.errorMessage != null) {
                println("   ⚠️  Error: ${result.errorMessage}")
            }
        }

        println()
        println("Test plan execution completed.")
        
        return results
    }

    /**
     * Create test plan on conformance suite
     * 
     * POST /api/plan
     * {
     *   "planName": "oid4vp-1final-wallet-haip-test-plan",
     *   "variant": {
     *     "credential_format": "sd_jwt_vc",
     *     "client_id_prefix": "x509_san_dns",
     *     ...
     *   },
     *   "configuration": {
     *     "wallet": {
     *       "wallet_endpoint": "http://localhost:7002/wallet/present"
     *     }
     *   }
     * }
     * 
     * @return Test plan ID
     */
    private suspend fun createTestPlan(): String {
        val requestBody = buildJsonObject {
            put("planName", JsonPrimitive(testPlan.planName))
            put("variant", testPlan.toVariantJson())
            putJsonObject("configuration") {
                putJsonObject("wallet") {
                    put("wallet_endpoint", JsonPrimitive("${testPlan.walletApiUrl}/wallet/present"))
                }
                
                // HAIP-specific configuration
                if (testPlan.haipMode) {
                    putJsonObject("security") {
                        put("require_signed_request", JsonPrimitive(true))
                        put("require_encrypted_response", JsonPrimitive(true))
                        put("require_holder_binding", JsonPrimitive(true))
                        put("allowed_curves", JsonArray(listOf(JsonPrimitive("P-256"))))
                        put("allowed_hash_algorithms", JsonArray(listOf(JsonPrimitive("SHA-256"))))
                    }
                }
            }
            put("description", JsonPrimitive(testPlan.describe()))
            put("publish", JsonPrimitive("everything"))
        }

        val response: HttpResponse = httpClient.post("https://$conformanceHost:$conformancePort/api/plan") {
            contentType(ContentType.Application.Json)
            setBody(requestBody.toString())
        }

        val responseBody = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        return responseBody["id"]?.jsonPrimitive?.content
            ?: throw IllegalStateException("No test plan ID in response")
    }

    /**
     * Get list of test modules in a test plan
     * 
     * GET /api/plan/{testPlanId}/modules
     * 
     * @return List of module IDs
     */
    private suspend fun getTestModules(testPlanId: String): List<String> {
        val response: HttpResponse = httpClient.get(
            "https://$conformanceHost:$conformancePort/api/plan/$testPlanId/modules"
        )

        val responseBody = Json.parseToJsonElement(response.bodyAsText())
        return responseBody.jsonArray.map { it.jsonPrimitive.content }
    }

    /**
     * Run a single test module
     * 
     * Flow:
     * 1. POST /api/plan/{testPlanId}/module/{moduleId}/start
     * 2. Conformance suite generates authorization request
     * 3. Conformance suite calls wallet endpoint (configured in test plan)
     * 4. Wallet processes request and sends response
     * 5. GET /api/plan/{testPlanId}/module/{moduleId}/result
     * 
     * @return Test result
     */
    private suspend fun runTestModule(testPlanId: String, moduleId: String): TestPlanResult {
        try {
            // Start test module
            val startResponse: HttpResponse = httpClient.post(
                "https://$conformanceHost:$conformancePort/api/plan/$testPlanId/module/$moduleId/start"
            )

            if (!startResponse.status.isSuccess()) {
                return TestPlanResult(
                    conformanceTestId = moduleId,
                    conformanceResult = "ERROR",
                    walletStatus = "FAILED",
                    errorMessage = "Failed to start module: ${startResponse.status}"
                )
            }

            // Wait for module to complete (conformance suite will call wallet endpoint)
            // The wallet endpoint will be called by conformance suite automatically
            // We just need to wait for the result

            // Poll for result (with timeout)
            var attempts = 0
            val maxAttempts = 30 // 30 seconds timeout
            var resultResponse: HttpResponse

            do {
                kotlinx.coroutines.delay(1000) // Wait 1 second between polls
                resultResponse = httpClient.get(
                    "https://$conformanceHost:$conformancePort/api/plan/$testPlanId/module/$moduleId/result"
                )
                attempts++
            } while (!resultResponse.status.isSuccess() && attempts < maxAttempts)

            if (!resultResponse.status.isSuccess()) {
                return TestPlanResult(
                    conformanceTestId = moduleId,
                    conformanceResult = "TIMEOUT",
                    walletStatus = "FAILED",
                    errorMessage = "Module execution timed out after ${maxAttempts}s"
                )
            }

            // Parse result
            val resultBody = Json.parseToJsonElement(resultResponse.bodyAsText()).jsonObject
            val conformanceStatus = resultBody["status"]?.jsonPrimitive?.content ?: "UNKNOWN"
            val walletStatus = resultBody["walletStatus"]?.jsonPrimitive?.content ?: "UNKNOWN"
            val errorMessage = resultBody["error"]?.jsonPrimitive?.content

            return TestPlanResult(
                conformanceTestId = moduleId,
                conformanceResult = conformanceStatus,
                walletStatus = walletStatus,
                errorMessage = errorMessage
            )

        } catch (e: Exception) {
            return TestPlanResult(
                conformanceTestId = moduleId,
                conformanceResult = "ERROR",
                walletStatus = "FAILED",
                errorMessage = "Exception: ${e.message}"
            )
        }
    }
}
