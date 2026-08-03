package id.walt.openid4vp.conformance.testplans

import id.walt.openid4vp.conformance.adapter.VciWalletConformanceAdapter
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.httpdata.CreateTestPlanResponse
import id.walt.openid4vp.conformance.testplans.plans.TestPlanResult
import id.walt.openid4vp.conformance.testplans.plans.vci.wallet.Oid4vciWalletVariantPlan
import id.walt.openid4vp.conformance.testplans.plans.vci.wallet.WalletVariant
import id.walt.openid4vp.conformance.testplans.plans.vci.wallet.WalletVariantMatrix
import id.walt.openid4vp.conformance.testplans.plans.vci.wallet.WalletVariantModuleRunResult
import id.walt.openid4vp.conformance.testplans.plans.vci.wallet.WalletVariantReportWriter
import id.walt.openid4vp.conformance.testplans.plans.vci.wallet.WalletVariantRunResult
import id.walt.openid4vp.conformance.testplans.plans.vci.wallet.WalletVariantRunStatus
import id.walt.openid4vp.conformance.testplans.plans.vci.wallet.WalletVariantSelection
import id.walt.openid4vp.conformance.testplans.runner.WalletBrowserAutomationConfig
import id.walt.openid4vp.conformance.testplans.runner.WalletConformanceBrowserAutomation
import id.walt.openid4vp.conformance.testplans.runner.walletBrowserInteractionsForAutomation
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.minutes

/** Runs the suite's OpenID4VCI wallet test plans against a running Wallet API2. */
class WalletConformanceTestRunner(
    private val runtime: WalletConformanceRuntimeConfig = WalletConformanceRuntimeConfig.fromEnvironment(),
    private val selection: WalletVariantSelection = WalletVariantSelection.fromEnvironment(),
) {
    suspend fun run(): List<TestPlanResult> {
        val walletClient = HttpClient {
            install(ContentNegotiation) { json() }
            install(HttpTimeout) {
                connectTimeoutMillis = 30_000
                requestTimeoutMillis = 120_000
            }
            expectSuccess = false
        }
        val conformance = ConformanceInterface(runtime.conformanceHost, runtime.conformancePort)
        val adapter = VciWalletConformanceAdapter(
            walletApiUrl = runtime.walletApiUrl,
            adapterPort = runtime.adapterPort,
            publicBaseUrl = runtime.adapterPublicUrl,
            clientId = runtime.clientId,
            txCode = runtime.txCode,
            clientAttestationIssuer = runtime.clientAttestationIssuer,
            clientAttesterJwk = runtime.clientAttesterJwks.singleAttesterJwk(),
        )

        try {
            checkWalletReachable(walletClient)
            check(conformance.getServerVersion() != null) {
                "OpenID conformance suite is unavailable at https://${runtime.conformanceHost}:${runtime.conformancePort}/api/server"
            }
            adapter.start(walletClient)

            val allVariants = WalletVariantMatrix.all()
            val selectedVariants = selection.select(allVariants)
            require(selectedVariants.isNotEmpty()) {
                "No OpenID4VCI wallet variants selected. Check OPENID4VCI_WALLET_CONFORMANCE_VARIANTS and filters."
            }
            println("Selected OpenID4VCI wallet variants: ${selectedVariants.size}/${allVariants.size}")

            val results = mutableListOf<WalletVariantRunResult>()
            for ((index, variant) in selectedVariants.withIndex()) {
                println("Running wallet matrix variant ${index + 1}/${selectedVariants.size}: ${variant.id}")
                results += runVariant(conformance, variant)
            }
            WalletVariantReportWriter.write(selection.reportDir, results)
            println("Wrote wallet conformance matrix artifacts to ${selection.reportDir}")

            if (selection.strictResults) {
                val failures = results.filter { it.status != WalletVariantRunStatus.PASSED }
                require(failures.isEmpty()) {
                    "OpenID4VCI wallet matrix failed for ${failures.size} variants. See ${selection.reportDir}/summary.md"
                }
            }
            return results.flatMap { result ->
                result.modules.map { module ->
                    TestPlanResult(
                        testName = module.testModule,
                        conformanceTestId = module.testId ?: result.variantId,
                        conformanceStatus = module.status ?: result.status.name,
                        conformanceResult = module.result,
                        errorMessage = module.error ?: result.error,
                    )
                }
            }
        } finally {
            adapter.close()
            conformance.close()
            walletClient.close()
        }
    }

    private suspend fun runVariant(conformance: ConformanceInterface, variant: WalletVariant): WalletVariantRunResult {
        unsupportedExecutionReason(variant)?.let { reason ->
            return WalletVariantRunResult(
                variantId = variant.id,
                variant = variant.toJsonObject(),
                status = WalletVariantRunStatus.BLOCKED,
                error = reason,
            )
        }

        return try {
            val plan = Oid4vciWalletVariantPlan(
                variantContext = variant,
                adapterPublicUrl = runtime.adapterPublicUrl,
                clientId = runtime.clientId,
                clientAttestationIssuer = runtime.clientAttestationIssuer,
                clientAttesterJwks = runtime.clientAttesterJwks,
                clientAttestationTrustAnchorPem = runtime.clientAttestationTrustAnchorPem,
                keyAttestationTrustAnchorPem = runtime.keyAttestationTrustAnchorPem,
                clientCertificatePem = runtime.clientCertificatePem,
            )
            val planId = createPlan(conformance, plan)
            val availableModules = planId.modules.filter { module ->
                selection.selectsModule(module.testModule, module.variant, variant)
            }
            require(availableModules.isNotEmpty()) {
                "No suite modules selected for ${variant.id}. Check module groups and module filters."
            }
            val browser = WalletConformanceBrowserAutomation(
                config = runtime.browserAutomation,
                adapterPublicUrl = runtime.adapterPublicUrl,
                conformanceHost = runtime.conformanceHost,
                conformancePort = runtime.conformancePort,
            )
            val moduleResults = mutableListOf<WalletVariantModuleRunResult>()
            for (module in availableModules) {
                moduleResults += runModule(conformance, planId.id, module, variant, browser)
            }
            WalletVariantRunResult(
                variantId = variant.id,
                variant = variant.toJsonObject(),
                planId = planId.id,
                status = if (moduleResults.all { it.accepted }) WalletVariantRunStatus.PASSED else WalletVariantRunStatus.FAILED,
                modules = moduleResults,
            )
        } catch (error: Throwable) {
            WalletVariantRunResult(
                variantId = variant.id,
                variant = variant.toJsonObject(),
                status = WalletVariantRunStatus.FAILED,
                error = "${error.javaClass.simpleName}: ${error.message}",
            )
        }
    }

    private suspend fun createPlan(
        conformance: ConformanceInterface,
        plan: Oid4vciWalletVariantPlan,
    ): CreateTestPlanResponse {
        val url = conformance.createTestPlanUrlWithConfig {
            append("planName", plan.planName)
            append("variant", plan.variantContext.testPlanCreationVariant().toString())
        }
        return conformance.createTestPlan(url, plan.configuration)
    }

    private suspend fun runModule(
        conformance: ConformanceInterface,
        planId: String,
        module: CreateTestPlanResponse.Module,
        planVariant: WalletVariant,
        browser: WalletConformanceBrowserAutomation,
    ): WalletVariantModuleRunResult {
        val testUrl = conformance.buildCreateTestUrl(planId, module.testModule, module.variant)
        val testId = conformance.createTest(testUrl).id
        val logUrl = "https://${runtime.conformanceHost}:${runtime.conformancePort}/log-detail.html?log=$testId"
        println("  ${module.testModule}: $logUrl")

        val attemptedUrls = mutableSetOf<String>()
        repeat(runtime.moduleTimeoutMinutes.toInt() * 120) {
            delay(500)
            val info = conformance.getTestRunInfo(testId)
            // Fetch this before returning a terminal state. Some wallet modules publish
            // their browser URL and then fail quickly; the full run contains the actual
            // failure detail that /api/info intentionally omits.
            val testRun = conformance.getTestRun(testId)

            if (info.status == "WAITING" && runtime.browserAutomation.enabled) {
                val interactions = testRun.walletBrowserInteractionsForAutomation()
                    .filter { it.url !in attemptedUrls }
                interactions.forEach { interaction ->
                    attemptedUrls += interaction.url
                    try {
                        browser.complete(interaction)
                    } catch (error: Throwable) {
                        // The suite otherwise waits forever for protocol requests that a
                        // failed local wallet adapter can no longer make.
                        runCatching {
                            conformance.cancelTest(testId)
                        }.onFailure {
                            println("Warning: could not cancel failed wallet test $testId: ${it.message}")
                        }
                        throw error
                    } finally {
                        // Preserve the same browser-URL audit state as the suite UI.
                        runCatching {
                            conformance.markBrowserUrlVisited(testId, interaction.url)
                        }.onFailure {
                            println("Warning: could not mark wallet browser URL as visited: ${it.message}")
                        }
                    }
                }
            }

            if (info.status in setOf("FINISHED", "INTERRUPTED")) {
                val accepted = info.status == "FINISHED" && info.result in setOf("PASSED", "SKIPPED")
                return WalletVariantModuleRunResult(
                    testModule = module.testModule,
                    testId = testId,
                    logUrl = logUrl,
                    status = info.status,
                    result = info.result,
                    accepted = accepted,
                    error = if (accepted) null else testRun.error.compactError()
                        ?: info.summary
                        ?: "Conformance result: ${info.result ?: info.status}",
                    variant = mergeVariant(planVariant, module.variant),
                )
            }
        }

        return WalletVariantModuleRunResult(
            testModule = module.testModule,
            testId = testId,
            logUrl = logUrl,
            status = "TIMEOUT",
            result = "TIMEOUT",
            error = "Module did not complete within ${runtime.moduleTimeoutMinutes} minutes",
            variant = mergeVariant(planVariant, module.variant),
        )
    }

    private suspend fun checkWalletReachable(client: HttpClient) {
        val response = client.get("${runtime.walletApiUrl}/wallet")
        check(response.status.value in 200..299) {
            "Wallet API2 is unavailable at ${runtime.walletApiUrl}/wallet: ${response.status}; ${response.bodyAsText().take(500)}"
        }
    }

    private fun unsupportedExecutionReason(variant: WalletVariant): String? = when (variant.authorizationCodeFlowVariant) {
        "wallet_initiated" ->
            "Wallet2 has no API for starting a wallet-initiated OpenID4VCI authorization-code flow without a credential offer."
        "issuer_initiated_dc_api" ->
            "Wallet2 has no Digital Credentials API request handler for issuer_initiated_dc_api conformance flows."
        else -> null
    }

    private fun mergeVariant(planVariant: WalletVariant, moduleVariant: JsonObject): JsonObject = buildJsonObject {
        planVariant.toJsonObject().forEach { (key, value) -> put(key, value) }
        moduleVariant.forEach { (key, value) -> put(key, value) }
    }

    private fun JsonObject.singleAttesterJwk(): JsonObject {
        val keys = this["keys"] as? kotlinx.serialization.json.JsonArray
        return keys?.singleOrNull() as? JsonObject ?: this
    }

    private fun JsonElement?.compactError(): String? = this
        ?.toString()
        ?.trim('"')
        ?.takeIf { it.isNotBlank() && it != "null" }
}
