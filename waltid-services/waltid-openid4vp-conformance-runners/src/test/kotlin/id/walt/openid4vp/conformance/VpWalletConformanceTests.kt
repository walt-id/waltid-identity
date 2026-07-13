package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.adapter.VpWalletConformanceAdapter
import id.walt.openid4vp.conformance.config.ConformanceConfig
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.plans.vp.wallet.VpWalletMdlX509HashRequestUriSignedDirectPostHaip
import id.walt.openid4vp.conformance.testplans.plans.vp.wallet.VpWalletMdlX509SanDnsRequestUriSignedDirectPost
import id.walt.openid4vp.conformance.testplans.plans.vp.wallet.VpWalletNegativeTests
import id.walt.openid4vp.conformance.testplans.plans.vp.wallet.VpWalletSdJwtVcX509HashRequestUriSignedDirectPostHaip
import id.walt.openid4vp.conformance.testplans.plans.vp.wallet.VpWalletSdJwtVcX509SanDnsRequestUriSignedDirectPost
import id.walt.openid4vp.conformance.testplans.plans.vp.wallet.WalletTestPlan
import id.walt.openid4vp.conformance.testplans.runner.WalletTestPlanRunner
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.condition.EnabledIf
import kotlin.test.Test

/**
 * VP Wallet Conformance Tests
 * 
 * Tests wallet-side OpenID4VP compliance against the OpenID Foundation conformance suite.
 * 
 * ## Prerequisites
 * 
 * 1. OpenID conformance suite running:
 *    ```bash
 *    cd ~/dev/openid/conformance-suite
 *    docker compose -f docker-compose-walt.yml up -d
 *    ```
 * 
 * 2. /etc/hosts entry: `127.0.0.1 localhost.emobix.co.uk`
 * 
 * 3. JVM truststore configured (handled by build.gradle.kts)
 * 
 * ## Running Tests
 * 
 * Run the isolated test (currently working):
 * ```bash
 * ./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
 *     --tests "IsolatedWalletConformanceTest"
 * ```
 * 
 * ## Known Issues
 * 
 * - Test plan creation works (verified)
 * - `getTestModules()` returns 404 - API path needs fixing
 * - Full test suite in this class has timeout issues (see IsolatedWalletConformanceTest for working pattern)
 * 
 * @see IsolatedWalletConformanceTest for the working isolated test pattern
 */
class VpWalletConformanceTests {

    companion object {
        val walletApiUrl: String = ConformanceConfig.WALLET_API_URL
        val adapterPort: Int = ConformanceConfig.WALLET_ADAPTER_PORT
        val adapterUrl: String = ConformanceConfig.WALLET_ADAPTER_URL
        val conformanceHost: String = ConformanceConfig.CONFORMANCE_HOST
        val conformancePort: Int = ConformanceConfig.CONFORMANCE_PORT

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

    // ========================================================================
    // HAIP Tests (x509_hash) - Required for HAIP compliance
    // ========================================================================

    /**
     * SD-JWT VC + x509_hash + request_uri_signed + direct_post.jwt (HAIP)
     * 
     * This is the primary HAIP compliance test for SD-JWT VC credentials.
     */
    @Test
    @EnabledIf("isConformanceAvailable")
    fun `VP Wallet - SD-JWT VC HAIP`() = runBlocking {
        val httpClient = createHttpClient()
        val adapter = VpWalletConformanceAdapter(
            walletApiUrl = walletApiUrl,
            adapterPort = adapterPort
        )
        
        try {
            adapter.start(httpClient)
            val plan = VpWalletSdJwtVcX509HashRequestUriSignedDirectPostHaip(adapterUrl, conformanceHost, conformancePort)
            WalletTestPlanRunner(plan, httpClient, conformanceHost, conformancePort).test()
        } finally {
            adapter.stop()
            httpClient.close()
        }
    }

    /**
     * mDL + x509_hash + request_uri_signed + direct_post.jwt (HAIP)
     * 
     * This is the primary HAIP compliance test for mDL credentials.
     */
    @Test
    @EnabledIf("isConformanceAvailable")
    fun `VP Wallet - mDL HAIP`() = runBlocking {
        val httpClient = createHttpClient()
        val adapter = VpWalletConformanceAdapter(
            walletApiUrl = walletApiUrl,
            adapterPort = adapterPort
        )
        
        try {
            adapter.start(httpClient)
            val plan = VpWalletMdlX509HashRequestUriSignedDirectPostHaip(adapterUrl, conformanceHost, conformancePort)
            WalletTestPlanRunner(plan, httpClient, conformanceHost, conformancePort).test()
        } finally {
            adapter.stop()
            httpClient.close()
        }
    }

    // ========================================================================
    // Standard Tests (x509_san_dns) - Non-HAIP variants
    // ========================================================================

    /**
     * SD-JWT VC + x509_san_dns + request_uri_signed + direct_post.jwt
     */
    @Test
    @EnabledIf("isConformanceAvailable")
    fun `VP Wallet - SD-JWT VC`() = runBlocking {
        val httpClient = createHttpClient()
        val adapter = VpWalletConformanceAdapter(
            walletApiUrl = walletApiUrl,
            adapterPort = adapterPort
        )
        
        try {
            adapter.start(httpClient)
            val plan = VpWalletSdJwtVcX509SanDnsRequestUriSignedDirectPost(adapterUrl, conformanceHost, conformancePort)
            WalletTestPlanRunner(plan, httpClient, conformanceHost, conformancePort).test()
        } finally {
            adapter.stop()
            httpClient.close()
        }
    }

    /**
     * mDL + x509_san_dns + request_uri_signed + direct_post.jwt
     */
    @Test
    @EnabledIf("isConformanceAvailable")
    fun `VP Wallet - mDL`() = runBlocking {
        val httpClient = createHttpClient()
        val adapter = VpWalletConformanceAdapter(
            walletApiUrl = walletApiUrl,
            adapterPort = adapterPort
        )
        
        try {
            adapter.start(httpClient)
            val plan = VpWalletMdlX509SanDnsRequestUriSignedDirectPost(adapterUrl, conformanceHost, conformancePort)
            WalletTestPlanRunner(plan, httpClient, conformanceHost, conformancePort).test()
        } finally {
            adapter.stop()
            httpClient.close()
        }
    }

    // ========================================================================
    // Negative Tests - Security Validation
    // ========================================================================

    /**
     * Negative Tests - Verify wallet correctly rejects invalid requests
     */
    @Test
    @EnabledIf("isConformanceAvailable")
    fun `VP Wallet - Negative Tests`() = runBlocking {
        val httpClient = createHttpClient()
        val adapter = VpWalletConformanceAdapter(
            walletApiUrl = walletApiUrl,
            adapterPort = adapterPort
        )
        
        try {
            adapter.start(httpClient)
            val plan = VpWalletNegativeTests(adapterUrl, conformanceHost, conformancePort)
            WalletTestPlanRunner(plan, httpClient, conformanceHost, conformancePort).test()
        } finally {
            adapter.stop()
            httpClient.close()
        }
    }

    // ========================================================================
    // Combined Test Suites
    // ========================================================================

    /**
     * Run all HAIP wallet test plans (x509_hash)
     * 
     * Use this for HAIP compliance validation.
     */
    @Test
    @EnabledIf("isConformanceAvailable")
    fun `Run all VP Wallet HAIP conformance tests`() = runBlocking {
        val httpClient = createHttpClient()
        val adapter = VpWalletConformanceAdapter(
            walletApiUrl = walletApiUrl,
            adapterPort = adapterPort
        )
        
        val testPlans: List<WalletTestPlan> = listOf(
            VpWalletSdJwtVcX509HashRequestUriSignedDirectPostHaip(adapterUrl, conformanceHost, conformancePort),
            VpWalletMdlX509HashRequestUriSignedDirectPostHaip(adapterUrl, conformanceHost, conformancePort),
            VpWalletNegativeTests(adapterUrl, conformanceHost, conformancePort)
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

    /**
     * Run all VP Wallet test plans (all variants)
     */
    @Test
    @EnabledIf("isConformanceAvailable")
    fun `Run all VP Wallet conformance tests`() = runBlocking {
        val httpClient = createHttpClient()
        val adapter = VpWalletConformanceAdapter(
            walletApiUrl = walletApiUrl,
            adapterPort = adapterPort
        )
        
        val testPlans: List<WalletTestPlan> = listOf(
            // HAIP variants (x509_hash)
            VpWalletSdJwtVcX509HashRequestUriSignedDirectPostHaip(adapterUrl, conformanceHost, conformancePort),
            VpWalletMdlX509HashRequestUriSignedDirectPostHaip(adapterUrl, conformanceHost, conformancePort),
            // Standard variants (x509_san_dns)
            VpWalletSdJwtVcX509SanDnsRequestUriSignedDirectPost(adapterUrl, conformanceHost, conformancePort),
            VpWalletMdlX509SanDnsRequestUriSignedDirectPost(adapterUrl, conformanceHost, conformancePort),
            // Negative tests
            VpWalletNegativeTests(adapterUrl, conformanceHost, conformancePort)
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
