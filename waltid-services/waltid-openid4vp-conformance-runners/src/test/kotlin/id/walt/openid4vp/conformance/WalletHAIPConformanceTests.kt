package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.adapter.WalletConformanceAdapter
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.wallet.WalletHAIPPlan1
import id.walt.openid4vp.conformance.testplans.wallet.WalletHAIPPlan2
import id.walt.openid4vp.conformance.testplans.wallet.WalletHAIPPlan7
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
 * HAIP (High Assurance Interoperability Profile) Wallet Conformance Tests
 * 
 * These tests validate wallet-side HAIP compliance:
 * - Signed request authentication (MANDATORY for HAIP)
 * - Encrypted response generation (MANDATORY for HAIP)
 * - P-256 key curve enforcement (MANDATORY for HAIP)
 * - SHA-256 hash algorithm (MANDATORY for HAIP)
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
 * docker compose -f docker-compose-local.yml up -d
 * ```
 */
class WalletHAIPConformanceTests {

    companion object {
        val walletApiUrl: String = "http://127.0.0.1:7005"
        val adapterPort: Int = 7006
        val adapterUrl: String = "http://127.0.0.1:$adapterPort/openid4vp/authorize"
        val conformanceHost: String = "localhost.emobix.co.uk"
        val conformancePort: Int = 8443

        private val testPlans: List<WalletTestPlan> = listOf(
            WalletHAIPPlan1(adapterUrl, conformanceHost, conformancePort),
            WalletHAIPPlan2(adapterUrl, conformanceHost, conformancePort),
            WalletHAIPPlan7(adapterUrl, conformanceHost, conformancePort)
        )

        val conformanceServerVersionResult = runBlocking {
            runCatching {
                ConformanceInterface(conformanceHost, conformancePort).getServerVersion()
            }.onFailure {
                println("INFO: Conformance suite not available")
                println("To run these tests:")
                println("  1. cd ~/dev/openid/conformance-suite")
                println("  2. docker compose -f docker-compose-local.yml up -d")
                println("  3. Wait ~30s for startup")
                println("  4. Verify: curl -k https://localhost.emobix.co.uk:8443/")
            }
        }

        @JvmStatic
        val isConformanceAvailable = conformanceServerVersionResult.isSuccess

        init {
            println()
            println("=" .repeat(80))
            println("HAIP Wallet Conformance Tests")
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
     * HAIP Plan 1: SD-JWT VC Baseline
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
    fun `HAIP Plan 1 - SD-JWT VC + x509_san_dns + direct_post_jwt`() = runTest(timeout = 10.minutes) {
        val httpClient = createHttpClient()
        val adapter = WalletConformanceAdapter(
            walletApiUrl = walletApiUrl,
            adapterPort = adapterPort
        )
        
        try {
            // Start adapter
            adapter.start(httpClient)
            
            // Run test
            val plan = WalletHAIPPlan1(adapterUrl, conformanceHost, conformancePort)
            WalletTestPlanRunner(plan, httpClient, conformanceHost, conformancePort).test()
        } finally {
            adapter.stop()
            httpClient.close()
        }
    }

    /**
     * HAIP Plan 2: mDL (Mobile Driving License) Baseline
     * 
     * Tests:
     * - Signed request authentication (x509_san_dns)
     * - Encrypted response generation (direct_post.jwt)
     * - DeviceAuth holder binding (MSO + DeviceSignature)
     * - Session transcript validation (Annex C)
     */
    @Test
    @EnabledIf("isConformanceAvailable")
    fun `HAIP Plan 2 - mDL + x509_san_dns + direct_post_jwt`() = runTest(timeout = 10.minutes) {
        val httpClient = createHttpClient()
        val adapter = WalletConformanceAdapter(
            walletApiUrl = walletApiUrl,
            adapterPort = adapterPort
        )
        
        try {
            // Start adapter
            adapter.start(httpClient)
            
            // Run test
            val plan = WalletHAIPPlan2(adapterUrl, conformanceHost, conformancePort)
            WalletTestPlanRunner(plan, httpClient, conformanceHost, conformancePort).test()
        } finally {
            adapter.stop()
            httpClient.close()
        }
    }

    /**
     * HAIP Plan 7: Negative Tests (Security Validation)
     * 
     * Tests that wallet correctly rejects non-HAIP-compliant requests.
     */
    @Test
    @EnabledIf("isConformanceAvailable")
    fun `HAIP Plan 7 - Negative Tests (Security Validation)`() = runTest(timeout = 10.minutes) {
        val httpClient = createHttpClient()
        val adapter = WalletConformanceAdapter(
            walletApiUrl = walletApiUrl,
            adapterPort = adapterPort
        )
        
        try {
            // Start adapter
            adapter.start(httpClient)
            
            // Run test
            val plan = WalletHAIPPlan7(adapterUrl, conformanceHost, conformancePort)
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
    fun runAllHAIPConformanceTests() = runTest(timeout = 60.minutes) {
        val httpClient = createHttpClient()
        val adapter = WalletConformanceAdapter(
            walletApiUrl = walletApiUrl,
            adapterPort = adapterPort
        )
        
        try {
            // Start adapter once for all tests
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
