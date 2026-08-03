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
