package id.walt.openid4vp.conformance.testplans.runner

import id.walt.openid4vp.conformance.report.ConformanceCiFlags
import id.walt.openid4vp.conformance.report.ConformanceReportWriter
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.httpdata.CreateTestPlanResponse
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import id.walt.openid4vp.conformance.testplans.plans.vp.wallet.WalletTestPlan
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*

/** Executes a single OpenID4VP wallet conformance test plan. */
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

        val planResponse = createTestPlan()
        val testPlanId = planResponse.id
        println("Test plan created: $testPlanId")
        println("View plan: https://$conformanceHost:$conformancePort/plan-detail.html?plan=$testPlanId")

        val modules = planResponse.modules
        println("Test modules: ${modules.size}")
        modules.forEach { println("   - ${it.testModule}") }
        println()

        val results = modules.mapIndexed { index, module ->
            println("[${index + 1}/${modules.size}] Running module: ${module.testModule}")
            val result = runModule(testPlanId, module)

            println("   Result: ${result.walletStatus}")
            if (result.errorMessage != null) {
                println("   Error: ${result.errorMessage}")
            }
            println()
            result
        }

        val namedResults = results.mapIndexed { index, result ->
            result.copy(
                testName = result.testName.takeIf { it != "unknown" }
                    ?: "${testPlan.description}#${index + 1}"
            )
        }
        ConformanceReportWriter.writeTestPlanResults(
            role = ConformanceReportWriter.Role.VP_WALLET,
            results = namedResults,
            conformanceHost = conformanceHost,
            conformancePort = conformancePort,
            expectRejection = testPlan.expectRejection,
        )
        printSummary(namedResults)

        return namedResults
    }

    private suspend fun createTestPlan(): CreateTestPlanResponse {
        val variantJson = Json.encodeToString(testPlan.variant)
        val createTestPlanUrl = conformance.createTestPlanUrlWithConfig {
            append("planName", testPlan.planName)
            append("variant", variantJson)
        }
        val body = buildJsonObject {
            put("configuration", testPlan.configuration)
        }
        val response = conformance.createTestPlan(createTestPlanUrl, body)

        println("Created test plan: ${response.id}")
        return response
    }

    /** Runs one suite module and returns its final conformance result. */
    private suspend fun runModule(testPlanId: String, module: CreateTestPlanResponse.Module): TestPlanResult {
        val moduleId = module.testModule

        try {
            val createTestUrl = conformance.buildCreateTestUrl(testPlanId, module.testModule, module.variant)
            println("   Creating test: $createTestUrl")

            val createTestResponse = conformance.createTest(createTestUrl)
            val testId = createTestResponse.id
            println("   Test ID: $testId")
            println("   View: https://$conformanceHost:$conformancePort/log-detail.html?log=$testId")

            conformance.waitForTestStatus(testId, shouldBeWaiting = true)

            val testRunResult = conformance.getTestRun(testId)
            println("   Test exposed endpoints available")

            val authEndpoint = testRunResult.getExposedAuthorizationEndpoint()
            println("   Authorization endpoint: $authEndpoint")
            val httpsEndpoint = authEndpoint.replace("http://", "https://")
            val walletResponse = conformanceHttp.get(httpsEndpoint)
            println("   Wallet response: ${walletResponse.status}")

            conformance.waitForTestStatus(testId, shouldBeWaiting = false)

            val testInfo = conformance.getTestRunInfo(testId)
            val conformanceResult = testInfo.result ?: "UNKNOWN"
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

        if (testPlan.optional || ConformanceCiFlags.allowFailure()) {
            return
        }

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
