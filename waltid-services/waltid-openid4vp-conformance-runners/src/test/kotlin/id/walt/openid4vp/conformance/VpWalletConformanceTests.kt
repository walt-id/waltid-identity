package id.walt.openid4vp.conformance

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.did.dids.DidService
import id.walt.openid4vp.conformance.adapter.VpWalletConformanceAdapter
import id.walt.openid4vp.conformance.config.ConformanceConfig
import id.walt.openid4vp.conformance.report.ConformanceReportWriter
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.keys.TestKeyMaterial
import id.walt.openid4vp.conformance.testplans.plans.vp.wallet.Oid4vpWalletVariantPlan
import id.walt.openid4vp.conformance.testplans.plans.vp.wallet.WalletCredentialFixture
import id.walt.openid4vp.conformance.testplans.runner.WalletTestPlanRunner
import id.walt.openid4vp.conformance.wallet.WalletCredentialIssuer
import id.walt.wallet2.OSSWallet2FeatureCatalog
import id.walt.wallet2.ClientIdTrustConfig
import id.walt.wallet2.OSSWallet2ServiceConfig
import id.walt.wallet2.handlers.ImportCredentialRequest
import id.walt.wallet2.server.handlers.CreateWalletRequest
import id.walt.wallet2.server.handlers.ImportKeyRequest
import id.walt.wallet2.server.handlers.WalletCreatedResponse
import id.walt.wallet2.wallet2Module
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.condition.EnabledIf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes

/**
 * OpenID4VP 1.0 wallet conformance tests.
 *
 * Wallet2 runs **in-process** through [E2ETest], exactly as Verifier2 does for the verifier tests -
 * no separately launched service and no fixed external port to coordinate. [VpWalletConformanceAdapter]
 * bridges the conformance suite's authorization endpoint to that in-process wallet.
 *
 * The wallet is provisioned with a credential issued by [WalletCredentialIssuer], so the run owns the
 * whole trust chain and the suite's `dcql_query` is guaranteed to match something in the store.
 */
class VpWalletConformanceTests {

    companion object {
        private const val WALLET_HOST = "127.0.0.1"
        private const val WALLET_PORT = 7015

        val conformanceHost: String = ConformanceConfig.CONFORMANCE_HOST
        val conformancePort: Int = ConformanceConfig.CONFORMANCE_PORT

        val conformanceServerVersionResult = runBlocking {
            runCatching {
                ConformanceInterface(conformanceHost, conformancePort).getServerVersion()
            }.onFailure { println("Conformance suite not available at $conformanceHost:$conformancePort: $it") }
        }

        @JvmStatic
        val isConformanceAvailable = conformanceServerVersionResult.isSuccess

        @JvmStatic
        @AfterAll
        fun writeSkippedSummaryIfSuiteUnavailable() {
            if (isConformanceAvailable) return
            ConformanceReportWriter.writeSkippedIfEmpty(
                role = ConformanceReportWriter.Role.VP_WALLET,
                reason = "Conformance suite not available at $conformanceHost:$conformancePort",
            )
        }

        /**
         * Optional comma-separated substrings selecting which matrix points to run, e.g.
         * `-Dconformance.wallet.variants=x509sandns,x509hash`.
         *
         * The full matrix is 18 plans and a few hundred modules, which is far too slow a loop when
         * investigating one variant. Unset - the default, and what CI uses - runs everything.
         */
        private val selectedVariants: List<String> =
            System.getProperty("conformance.wallet.variants")
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()

        private fun isSelected(plan: Oid4vpWalletVariantPlan): Boolean =
            selectedVariants.isEmpty() || selectedVariants.any { it in plan.name }

        fun createHttpClient(): HttpClient = HttpClient(Java) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = ConformanceConfig.HTTP_REQUEST_TIMEOUT_MS
                connectTimeoutMillis = ConformanceConfig.HTTP_CONNECT_TIMEOUT_MS
            }
            expectSuccess = false
        }
    }

    @Test
    @EnabledIf("isConformanceAvailable")
    fun runWallet2ConformanceTests() {
        E2ETest(WALLET_HOST, WALLET_PORT, failEarly = true).testBlock(
            // 18 matrix points, and any module the suite cannot complete costs a 60 s poll budget
            // before it is recorded. A full run measured ~43 min, so 30 min truncated it and reported
            // an UncompletedCoroutinesError instead of the results that had already been collected.
            timeout = 90.minutes,

            features = listOf(OSSWallet2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "wallet-service",
                    OSSWallet2ServiceConfig(
                        publicBaseUrl = Url("http://$WALLET_HOST:$WALLET_PORT"),
                        // The suite signs its request objects with the certificate in `client.jwks`.
                        // Authenticating an x509_san_dns / x509_hash client identifier means chaining
                        // that certificate to a trust anchor, so the wallet has to be told about the CA
                        // or every signed request is rejected as unverifiable.
                        clientIdTrust = ClientIdTrustConfig(
                            x509TrustAnchors = listOf(TestKeyMaterial.CREDENTIAL_ISSUER_CA_PEM),
                        ),
                    )
                )
            },
            init = { DidService.minimalInit() },
            module = { wallet2Module(withPlugins = false) },
        ) {
            val wallet = testHttpClient()
            val walletId = provisionWallet(wallet)

            // The adapter is a separate server the suite calls; it forwards to the in-process wallet.
            val adapterHttp = createHttpClient()
            val adapter = VpWalletConformanceAdapter(
                walletApiUrl = "http://$WALLET_HOST:$WALLET_PORT",
                adapterPort = ConformanceConfig.WALLET_ADAPTER_PORT,
                walletId = walletId,
            )

            try {
                adapter.start(adapterHttp)
                Oid4vpWalletVariantPlan.supportedByWallet2(
                    walletApiUrl = ConformanceConfig.WALLET_ADAPTER_URL,
                    conformanceHost = conformanceHost,
                    conformancePort = conformancePort,
                ).filter(::isSelected).forEach { plan ->
                    println("\n" + "=".repeat(80))
                    println("Running wallet plan: ${plan.name}")
                    println("=".repeat(80))
                    runCatching {
                        WalletTestPlanRunner(plan, adapterHttp, conformanceHost, conformancePort).test()
                    }.onFailure { error ->
                        println("Plan ${plan.name} failed: ${error.message}")
                    }
                }
            } finally {
                adapter.stop()
                adapterHttp.close()
            }
        }
    }

    /**
     * Create a wallet, give it the holder key the credential is bound to, and import the credential.
     *
     * Returns the wallet id the adapter should present from.
     */
    private suspend fun E2ETest.provisionWallet(wallet: HttpClient): String {
        val issuer = WalletCredentialIssuer()

        val walletId = testAndReturn("Create wallet") {
            wallet.post("/wallet") {
                contentType(ContentType.Application.Json)
                setBody(CreateWalletRequest())
            }.also { assertEquals(HttpStatusCode.Created, it.status) }
                .body<WalletCreatedResponse>().walletId
        }

        test("Import holder key") {
            wallet.post("/wallet/$walletId/keys/import") {
                contentType(ContentType.Application.Json)
                setBody(ImportKeyRequest(key = issuer.holderSerializedKey()))
            }.also { assertEquals(HttpStatusCode.Created, it.status) }
        }

        test("Import SD-JWT VC bound to the holder key") {
            val credential = issuer.issueSdJwtVc()
            println("Provisioned credential (vct=${WalletCredentialFixture.SD_JWT_VC_VCT}): ${credential.take(80)}...")
            wallet.post("/wallet/$walletId/credentials/import") {
                contentType(ContentType.Application.Json)
                setBody(ImportCredentialRequest(rawCredential = credential, label = "conformance"))
            }.also { assertEquals(HttpStatusCode.Created, it.status) }
        }

        test("Import mDL bound to the same holder key as device key") {
            val mdl = issuer.issueMdl()
            println("Provisioned mDL (docType=${WalletCredentialFixture.MDOC_DOCTYPE}): ${mdl.take(60)}...")
            wallet.post("/wallet/$walletId/credentials/import") {
                contentType(ContentType.Application.Json)
                setBody(ImportCredentialRequest(rawCredential = mdl, label = "conformance-mdl"))
            }.also { assertEquals(HttpStatusCode.Created, it.status) }
        }

        test("Wallet holds both provisioned credentials") {
            val credentials = wallet.get("/wallet/$walletId/credentials").body<List<JsonObject>>()
            assertNotNull(credentials)
            assertEquals(2, credentials.size, "expected the SD-JWT VC and the mDL to be stored")
        }

        return walletId
    }
}
