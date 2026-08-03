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
