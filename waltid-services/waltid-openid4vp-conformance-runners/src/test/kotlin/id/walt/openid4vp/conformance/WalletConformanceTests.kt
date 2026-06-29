package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.adapter.WalletConformanceAdapter
import id.walt.openid4vp.conformance.config.ConformanceConfig
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.wallet.WalletPlan1
import id.walt.openid4vp.conformance.testplans.wallet.WalletPlan2
import id.walt.openid4vp.conformance.testplans.wallet.WalletPlan7
import id.walt.openid4vp.conformance.testplans.wallet.WalletTestPlan
import id.walt.openid4vp.conformance.testplans.runner.WalletTestPlanRunner
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.condition.EnabledIf
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

/**
 * Wallet Conformance Tests
 * 
 * Tests wallet-side OpenID4VP compliance against the OpenID Foundation conformance suite.
 * Includes HAIP (High Assurance Interoperability Profile) test plans for eIDAS 2.0 compliance.
 * 
 * HAIP Requirements validated:
 * - Signed request authentication (MANDATORY)
 * - Encrypted response generation (MANDATORY)
 * - P-256 key curve enforcement (MANDATORY)
 * - SHA-256 hash algorithm (MANDATORY)
 * - Holder binding (KB-JWT for SD-JWT, DeviceAuth for mdoc)
 * 
 * Prerequisites:
 * - OpenID conformance suite running on localhost.emobix.co.uk:8443
 * - /etc/hosts entry: 127.0.0.1 localhost.emobix.co.uk
 * - Trust store configured with conformance suite certificate
 * - Wallet implementation with HAIP support
 * 
 * Setup:
 * ```bash
 * cd ~/dev/openid/conformance-suite
 * docker compose -f docker-compose-walt.yml up -d
 * ```
 * 
 * Run:
 * ```bash
 * ./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test --tests "WalletConformanceTests"
 * ```
 */
class WalletConformanceTests {

    companion object {
        val walletApiUrl: String = ConformanceConfig.WALLET_API_URL
        val adapterPort: Int = ConformanceConfig.WALLET_ADAPTER_PORT
        val adapterUrl: String = "http://127.0.0.1:$adapterPort/openid4vp/authorize"
        val conformanceHost: String = ConformanceConfig.CONFORMANCE_HOST
        val conformancePort: Int = ConformanceConfig.CONFORMANCE_PORT

        private val testPlans: List<WalletTestPlan> = listOf(
            WalletPlan1(adapterUrl, conformanceHost, conformancePort),
            WalletPlan2(adapterUrl, conformanceHost, conformancePort),
            WalletPlan7(adapterUrl, conformanceHost, conformancePort)
        )

        val conformanceServerVersionResult = runBlocking {
            runCatching {
                ConformanceInterface(conformanceHost, conformancePort).getServerVersion()
            }.onFailure {
                println("INFO: Conformance suite not available")
                println("To run these tests:")
                println("  1. cd ~/dev/openid/conformance-suite")
                println("  2. docker compose -f docker-compose-walt.yml up -d")
                println("  3. Wait ~30s for startup")
                println("  4. Verify: curl -k https://localhost.emobix.co.uk:8443/")
            }
        }

        @JvmStatic
        val isConformanceAvailable = conformanceServerVersionResult.isSuccess

        init {
            println()
            println("=" .repeat(80))
            println("Wallet Conformance Tests (HAIP)")
            println("=" .repeat(80))
            println()
            
            if (isConformanceAvailable) {
                println("Conformance suite available: ${conformanceServerVersionResult.getOrNull()}")
            } else {
                println("INFO: Conformance suite not available - tests will be skipped")
            }
            
            println()
            println("Test plans:")
            testPlans.forEach { plan ->
                println("  - ${plan.description}")
            }
            println()
            println("=" .repeat(80))
            println()
        }

        fun createHttpClient(): HttpClient = HttpClient(Java) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    prettyPrint = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 30_000
            }
            expectSuccess = false
        }
    }

    // ================================
    // Phase 1: Core HAIP Validation (MVP)
    // ================================

    /**
     * Plan 1: SD-JWT VC Baseline (HAIP)
     * 
     * Tests:
     * - Signed request authentication (x509_san_dns)
     * - Encrypted response generation (direct_post.jwt)
     * - KB-JWT holder binding
     * - P-256 key curve
     * - SHA-256 hash algorithm
     */
    @Test
    @EnabledIf("isConformanceAvailable")
    fun `Plan 1 - SD-JWT VC + x509_san_dns + direct_post_jwt`() = runTest(timeout = 10.minutes) {
        val httpClient = createHttpClient()
        val adapter = WalletConformanceAdapter(
            walletApiUrl = walletApiUrl,
            adapterPort = adapterPort
        )
        
        try {
            adapter.start(httpClient)
            val plan = WalletPlan1(adapterUrl, conformanceHost, conformancePort)
            WalletTestPlanRunner(plan, httpClient, conformanceHost, conformancePort).test()
        } finally {
            adapter.stop()
            httpClient.close()
        }
    }

    /**
     * Plan 2: mDL (Mobile Driving License) Baseline (HAIP)
     * 
     * Tests:
     * - Signed request authentication (x509_san_dns)
     * - Encrypted response generation (direct_post.jwt)
     * - DeviceAuth holder binding (MSO + DeviceSignature)
     * - Session transcript validation (Annex C)
     */
    @Test
    @EnabledIf("isConformanceAvailable")
    fun `Plan 2 - mDL + x509_san_dns + direct_post_jwt`() = runTest(timeout = 10.minutes) {
        val httpClient = createHttpClient()
        val adapter = WalletConformanceAdapter(
            walletApiUrl = walletApiUrl,
            adapterPort = adapterPort
        )
        
        try {
            adapter.start(httpClient)
            val plan = WalletPlan2(adapterUrl, conformanceHost, conformancePort)
            WalletTestPlanRunner(plan, httpClient, conformanceHost, conformancePort).test()
        } finally {
            adapter.stop()
            httpClient.close()
        }
    }

    /**
     * Plan 7: Negative Tests (Security Validation)
     * 
     * Tests that wallet correctly rejects non-HAIP-compliant requests.
     */
    @Test
    @EnabledIf("isConformanceAvailable")
    fun `Plan 7 - Negative Tests (Security Validation)`() = runTest(timeout = 10.minutes) {
        val httpClient = createHttpClient()
        val adapter = WalletConformanceAdapter(
            walletApiUrl = walletApiUrl,
            adapterPort = adapterPort
        )
        
        try {
            adapter.start(httpClient)
            val plan = WalletPlan7(adapterUrl, conformanceHost, conformancePort)
            WalletTestPlanRunner(plan, httpClient, conformanceHost, conformancePort).test()
        } finally {
            adapter.stop()
            httpClient.close()
        }
    }

    /**
     * Comprehensive test suite runner
     * 
     * Runs all configured test plans.
     */
    @Test
    @EnabledIf("isConformanceAvailable")
    fun runAllWalletConformanceTests() = runTest(timeout = 60.minutes) {
        val httpClient = createHttpClient()
        val adapter = WalletConformanceAdapter(
            walletApiUrl = walletApiUrl,
            adapterPort = adapterPort
        )
        
        try {
            adapter.start(httpClient)
            
            testPlans.forEach { plan ->
                println()
                println("=" .repeat(80))
                println("Running: ${plan.description}")
                println("=" .repeat(80))
                
                WalletTestPlanRunner(plan, httpClient, conformanceHost, conformancePort).test()
            }
        } finally {
            adapter.stop()
            httpClient.close()
        }
    }
}
