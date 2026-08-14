package id.walt.openid4vp.conformance.testplans.runner

import id.walt.openid4vp.conformance.report.ConformanceCiFlags
import id.walt.openid4vp.conformance.report.ConformanceReportWriter
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import id.walt.openid4vp.conformance.testplans.plans.vci.wallet.VciWalletTestPlan
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

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

        modules.forEachIndexed { index, module ->
            println("[${index + 1}/${modules.size}] Running: ${module.testModule}")
            val result = runModule(testPlanId, module)
            results.add(result)
            println("   Status: ${result.conformanceResult}")
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
            role = ConformanceReportWriter.Role.VCI_WALLET,
            results = namedResults,
            conformanceHost = conformanceHost,
            conformancePort = conformancePort,
            producer = testPlan.producerId,
        )
        printSummary(namedResults)
        ConformanceReportWriter.failIfNeededFromTestPlanResults(
            role = ConformanceReportWriter.Role.VCI_WALLET,
            results = namedResults,
            allowFailure = ConformanceCiFlags.allowFailure(),
        )
        return namedResults
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

        deliverCredentialOfferToWallet(testId)
        println()

        val maxAttempts = MODULE_POLL_ATTEMPTS
        var attempts = 0

        while (attempts < maxAttempts) {
            delay(POLL_INTERVAL_MILLISECONDS)
            attempts++

            val testInfo = conformance.getTestRunInfo(testId)

            if (testInfo.status in setOf("FINISHED", "INTERRUPTED")) {
                val result = testInfo.result ?: "UNKNOWN"
                // The suite reports SKIPPED for a module it decided not to exercise - typically an
                // optional feature this wallet does not advertise. That is not a wallet failure, and
                // counting it as one made a clean run read as "6 passed, 6 failed".
                val skipped = result == "SKIPPED"
                return TestPlanResult(
                    conformanceTestId = testId,
                    conformanceResult = result,
                    walletStatus = result,
                    skipReason = "Suite skipped this module".takeIf { skipped },
                    errorMessage = if (result != "PASSED" && !skipped) {
                        "Test finished: $result"
                    } else null
                )
            }

            if (testInfo.status == "WAITING" && attempts % 20 == 0) {
                println("   Status: WAITING")
            }
        }

        // Every module of a plan shares the plan alias and the suite allows one holder at a time, so a
        // module left running is killed by the next one with "Stopping test due to alias conflict" -
        // overwriting the real reason it stalled. Cancel deliberately so this module keeps its own
        // diagnosis and the next one starts from a clean alias.
        conformance.cancelTest(testId)

        return TestPlanResult(
            conformanceTestId = testId,
            conformanceResult = "TIMEOUT",
            walletStatus = "TIMEOUT",
            errorMessage = "Module did not complete within ${MODULE_POLL_ATTEMPTS * POLL_INTERVAL_MILLISECONDS / 1000} seconds"
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

    /**
     * Hand the suite's credential offer to the wallet.
     *
     * The suite publishes the offer as an `openid-credential-offer://` deep link for a human to open;
     * nothing happens until someone does, which is why an automated run otherwise just times out.
     * Forwarding the offer parameter to the adapter is what a user tapping that link would achieve.
     */
    private suspend fun deliverCredentialOfferToWallet(testId: String) {
        // Poll for the offer URL rather than for a status: the authorization-code grant parks the test
        // in WAITING, but the pre-authorized code grant does not, so waiting on WAITING would time out
        // for a perfectly healthy pre-auth run.
        var offerUrl: String? = null
        repeat(OFFER_POLL_ATTEMPTS) {
            offerUrl = conformance.getTestRun(testId).getBrowserUrls().firstOrNull()
            if (offerUrl != null) return@repeat
            delay(POLL_INTERVAL_MILLISECONDS)
        }
        val resolvedOfferUrl = offerUrl
            ?: error("Conformance suite published no credential offer URL to hand to the wallet")

        // Parsed by hand rather than with Url(): the deep link uses a custom scheme.
        val parameters = parseQueryString(resolvedOfferUrl.substringAfter('?', ""))
        val (parameterName, parameterValue) = OFFER_PARAMETER_NAMES
            .firstNotNullOfOrNull { name -> parameters[name]?.let { name to it } }
            ?: error("Credential offer URL carries none of $OFFER_PARAMETER_NAMES: $resolvedOfferUrl")

        // POSTed with the wallet's own client: POST /credential-offer is the adapter's programmatic
        // entry point (GET renders a page for a human), and the conformance client pins its host and
        // protocol to the suite via defaultRequest, so it cannot address the adapter at all.
        println("   Delivering $parameterName to the wallet adapter")
        val response = walletHttpClient.post("$walletAdapterUrl/credential-offer") {
            parameter(parameterName, parameterValue)
        }
        val body = response.bodyAsText()
        // Printed untruncated on purpose: the adapter relays the wallet's own error body verbatim,
        // and that body is the only place a wallet-side failure reason ever appears.
        println("   Adapter accepted the offer: ${response.status} $body")
    }

    private companion object {
        /** Offer delivery is either by value or by reference, per OpenID4VCI 1.0. */
        val OFFER_PARAMETER_NAMES = listOf("credential_offer", "credential_offer_uri")

        /** ~15s of polling for the suite to publish its credential offer URL. */
        const val OFFER_POLL_ATTEMPTS = 30

        const val POLL_INTERVAL_MILLISECONDS = 500L

        /**
         * Safety net for a module that never reaches a verdict, at [POLL_INTERVAL_MILLISECONDS] each.
         *
         * A healthy module finishes in seconds - the whole four-module authorization-code plan runs in
         * about 13 s. This budget only matters when a module stalls, and the previous 5 minutes made a
         * fully stalling plan cost 20 minutes and the 22-module HAIP plan over an hour.
         *
         * Not lowered further than 90 s because the suite deliberately waits before deciding some
         * modules: `maxWaitForNotificationSeconds` is 20 s by default, and the plan configuration
         * grants the suite `waitTimeoutSeconds` to wait for the wallet. Cutting below those would
         * report a timeout for a module that was about to reach a legitimate verdict.
         */
        const val MODULE_POLL_ATTEMPTS = 180
    }

}
