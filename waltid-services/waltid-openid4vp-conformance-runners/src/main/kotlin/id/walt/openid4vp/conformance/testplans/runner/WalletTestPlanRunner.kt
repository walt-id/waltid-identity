package id.walt.openid4vp.conformance.testplans.runner

import id.walt.openid4vp.conformance.report.ConformanceCiFlags
import id.walt.openid4vp.conformance.report.ConformanceReportWriter
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.httpdata.CreateTestPlanResponse
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import id.walt.openid4vp.conformance.testplans.plans.vp.wallet.WalletModuleApplicability
import id.walt.openid4vp.conformance.testplans.plans.vp.wallet.WalletTestPlan
import id.walt.openid4vp.conformance.utils.JsonUtils.lenientJson
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        println("=".repeat(80))
        println("Test Plan: ${testPlan.description}")
        println("=".repeat(80))
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

        // Fetched once per plan: the suite publishes some modules for variants its own applicability
        // rules exclude, and running those produces failures that say nothing about the wallet.
        val moduleMetadata = conformance.getAvailableTestModules()

        // Run each module
        val results = mutableListOf<TestPlanResult>()
        modules.forEachIndexed { index, module ->
            println("[${index + 1}/${modules.size}] Running module: ${module.testModule}")

            val inapplicable = WalletModuleApplicability.inapplicableReason(
                testModule = module.testModule,
                moduleMetadata = moduleMetadata[module.testModule],
                variantSelection = testPlan.axisValues,
            )
            val result = if (inapplicable != null) {
                println("   Skipped: not applicable to this variant - $inapplicable")
                TestPlanResult(
                    testName = "${testPlan.producerId}/${module.testModule}",
                    conformanceTestId = module.testModule,
                    conformanceResult = "SKIPPED",
                    walletStatus = "SKIPPED",
                    skipReason = "Not applicable to this variant: $inapplicable",
                )
            } else {
                runModule(testPlanId, module)
            }
            results.add(result)

            println("   Result: ${result.walletStatus}")
            if (result.errorMessage != null) {
                println("   Error: ${result.errorMessage}")
            }
            println()
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
            producer = testPlan.producerId,
            expectRejection = testPlan.expectRejection,
        )
        printSummary(namedResults)

        return namedResults
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

        // POST /api/plan takes the test configuration as the raw request body (TestPlanApi
        // .createTestPlan(@RequestBody JsonObject config)). Wrapping it in another object makes the
        // suite store an empty configuration and every module then fails on missing config fields.
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
        // Held outside the try so a failure still reports the suite's test id: recording the module
        // name instead produced report rows whose log link pointed at a test that does not exist,
        // making exactly the failures that need investigating the ones that could not be investigated.
        var testId: String? = null

        try {
            // Create test instance for this module (same API as verifier tests)
            val createTestUrl = conformance.buildCreateTestUrl(testPlanId, module.testModule, module.variant)
            println("   Creating test: $createTestUrl")

            val createTestResponse = conformance.createTest(createTestUrl)
            testId = createTestResponse.id
            println("   Test ID: $testId")
            println("   View: https://$conformanceHost:$conformancePort/log-detail.html?log=$testId")

            // Wait for test to be ready (WAITING state)
            conformance.waitForTestStatus(testId, shouldBeWaiting = true)

            // In a wallet test plan the suite is the verifier: it publishes the authorization
            // request as a redirect at the wallet's authorization_endpoint (our adapter). Opening
            // that URL is what hands the request to the wallet, so there is no exposed
            // authorization_endpoint to read here - that only exists for verifier plans.
            val testRunResult = conformance.getTestRun(testId)
            val requestUrl = testRunResult.getBrowserUrls().firstOrNull()
                ?: error("Conformance suite published no browser URL to hand to the wallet")
            println("   Authorization request for the wallet: $requestUrl")

            val walletResponse = conformanceHttp.get(requestUrl)
            println("   Wallet (via adapter) responded: ${walletResponse.status}")
            conformance.markBrowserUrlVisited(testId, requestUrl)

            // OpenID4VP 1.0 8.2: a redirect_uri returned in the direct_post response must be opened
            // in the end-user's browser, not consumed as a back channel. The suite fails the module
            // if nobody visits it, so stand in for the browser here.
            walletResponse.walletRedirectTo()?.let { redirectTo ->
                println("   Opening redirect_uri from the direct_post response: $redirectTo")
                submitRedirectFragment(conformanceHttp.get(redirectTo), redirectTo)
            }

            // Wait for the test to complete. The negative modules ask for a screenshot of the
            // wallet's error page (ExpectRedirectUriErrorPage + waitForPlaceholders), so they only
            // leave WAITING once that evidence is supplied - same gate as the verifier happy flows.
            conformance.waitForTestStatus(testId, shouldBeWaiting = false, fulfillImagePlaceholders = true)

            // Get final result from test info
            val testInfo = conformance.getTestRunInfo(testId)
            val conformanceResult = testInfo.result ?: "UNKNOWN"

            // For wallet tests, wallet status == conformance result
            val walletStatus = when {
                testPlan.expectRejection && conformanceResult == "PASSED" -> "REJECTED"
                conformanceResult == "PASSED" -> "PASSED"
                conformanceResult == "FAILED" -> "FAILED"
                conformanceResult == "WARNING" -> "PASSED" // Warnings are acceptable
                // A module that ends in manual review has run to completion with no failed check;
                // the uploaded evidence is what a human would inspect.
                conformanceResult == "REVIEW" -> "PASSED"
                else -> "UNKNOWN"
            }

            return TestPlanResult(
                testName = "${testPlan.producerId}/$moduleId",
                conformanceTestId = testId,
                conformanceResult = conformanceResult,
                walletStatus = walletStatus,
                errorMessage = null
            )

        } catch (e: Exception) {
            return TestPlanResult(
                testName = "${testPlan.producerId}/$moduleId",
                conformanceTestId = testId ?: moduleId,
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
        println("-".repeat(80))
        println("  Total modules: ${results.size}")

        val passed = results.count { it.walletStatus == "PASSED" }
        val failed = results.count { it.walletStatus == "FAILED" }
        val rejected = results.count { it.walletStatus == "REJECTED" }
        val errors = results.count { it.walletStatus == "ERROR" }
        val timeouts = results.count { it.walletStatus == "TIMEOUT" }
        val skipped = results.count { it.skipReason != null }

        println("  Passed:  $passed")
        if (failed > 0) println("  Failed:  $failed")
        if (rejected > 0) println("  Rejected: $rejected")
        if (errors > 0) println("  Errors:  $errors")
        if (timeouts > 0) println("  Timeouts: $timeouts")
        if (skipped > 0) println("  Skipped: $skipped (not applicable to this variant)")
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

        println("=".repeat(80))
        println()

        if (testPlan.optional || ConformanceCiFlags.allowFailure()) {
            return
        }

        // Skipped modules were never run, so they can neither be rejected nor passed.
        val executed = results.filter { it.skipReason == null }
        if (testPlan.expectRejection) {
            val allRejected = executed.all { it.walletStatus == "REJECTED" || it.conformanceResult == "PASSED" }
            check(allRejected) {
                "Negative test plan expected all requests to be rejected by wallet, but some were accepted"
            }
        } else {
            val allPassed = executed.all { it.walletStatus == "PASSED" }
            check(allPassed) {
                "Test plan had ${executed.count { it.walletStatus != "PASSED" }} failures"
            }
        }
    }

    /**
     * `redirect_to` the wallet reported after posting the response, if any.
     *
     * Read defensively: a rejected request answers with an error body instead.
     */
    private suspend fun HttpResponse.walletRedirectTo(): String? = runCatching {
        val result = lenientJson.parseToJsonElement(bodyAsText()).jsonObject
        // `redirect_to` covers same-device flows; for direct_post the verifier hands the redirect back
        // in its acknowledgement body, which the wallet surfaces verbatim as `verifier_response`.
        result["redirect_to"]?.jsonPrimitive?.contentOrNull
            ?: result["verifier_response"]?.jsonObject?.get("redirect_uri")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    /**
     * Stand in for the browser on the suite's implicit-callback page.
     *
     * The page the suite serves at the redirect URI carries JavaScript that POSTs
     * `window.location.hash` to a generated submit URL; a URL fragment never reaches a server, so the
     * suite has no other way to learn it. Without that POST the module aborts with "The fragment has
     * not been submitted by the user's browser", which is what happens when the redirect is merely
     * fetched. Replicating the two calls avoids driving a real browser just to run three lines of JS.
     */
    private suspend fun submitRedirectFragment(redirectResponse: HttpResponse, redirectUrl: String) {
        val submitUrl = IMPLICIT_SUBMIT_PATTERN.find(redirectResponse.bodyAsText())?.groupValues?.get(1)
            // Thymeleaf escapes forward slashes when inlining the URL into JavaScript.
            ?.replace("\\/", "/")
        if (submitUrl == null) {
            println("   No implicit-submit URL on the redirect page - nothing to submit")
            return
        }
        // window.location.hash includes the '#', and is empty when the URL carries no fragment.
        val fragment = redirectUrl.substringAfter('#', "").let { if (it.isEmpty()) "" else "#$it" }
        println("   Submitting redirect fragment (${fragment.length} chars) to $submitUrl")
        conformanceHttp.post(submitUrl) { setBody(fragment) }
    }

    private companion object {
        /** Matches the submit URL in the suite's implicitCallback page: xhr.open('POST', '<url>', true) */
        val IMPLICIT_SUBMIT_PATTERN = Regex("""xhr\.open\('POST',\s*["']([^"']+)["']""")
    }
}
