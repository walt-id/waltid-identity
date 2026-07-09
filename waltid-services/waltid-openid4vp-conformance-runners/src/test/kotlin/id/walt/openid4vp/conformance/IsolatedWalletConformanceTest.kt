package id.walt.openid4vp.conformance

import id.walt.openid4vp.conformance.config.ConformanceConfig
import id.walt.openid4vp.conformance.testplans.http.ConformanceInterface
import id.walt.openid4vp.conformance.testplans.plans.vp.wallet.VpWalletSdJwtVcX509SanDnsRequestUriSignedDirectPost
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
 * Isolated Wallet Conformance Test
 * 
 * This test class demonstrates that the conformance API integration WORKS when run in isolation.
 * It successfully creates test plans against the OpenID conformance suite.
 * 
 * ## Why This Exists
 * 
 * The main `VpWalletConformanceTests` class has mysterious HTTP timeout issues during test plan
 * creation. This isolated test proves the HTTP client, SSL truststore, and API calls all work
 * correctly when run without complex companion object initialization.
 * 
 * ## Running
 * 
 * ```bash
 * ./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
 *     --tests "IsolatedWalletConformanceTest"
 * ```
 * 
 * ## Current Status
 * 
 * ✅ Test plan creation works (response: `Created test plan: <id>`)
 * ❌ `getTestModules()` returns 404 - API path needs investigation
 * 
 * ## Prerequisites
 * 
 * 1. Conformance suite running:
 *    ```bash
 *    cd ~/dev/openid/conformance-suite
 *    docker compose -f docker-compose-walt.yml up -d
 *    ```
 * 
 * 2. /etc/hosts: `127.0.0.1 localhost.emobix.co.uk`
 */
class IsolatedWalletConformanceTest {

    companion object {
        val conformanceHost: String = ConformanceConfig.CONFORMANCE_HOST
        val conformancePort: Int = ConformanceConfig.CONFORMANCE_PORT

        val conformanceServerVersionResult = runBlocking {
            runCatching {
                ConformanceInterface(conformanceHost, conformancePort).getServerVersion()
            }
        }

        @JvmStatic
        val isConformanceAvailable = conformanceServerVersionResult.isSuccess
    }

    @Test
    @EnabledIf("isConformanceAvailable")
    fun testWalletTestPlanRunner() = runBlocking {
        println("=== Isolated Wallet Conformance Test ===")
        
        val httpClient = HttpClient(Java) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 30_000
            }
            expectSuccess = false
        }
        
        try {
            val adapterUrl = "http://host.docker.internal:${ConformanceConfig.WALLET_ADAPTER_PORT}/openid4vp/authorize"
            val plan = VpWalletSdJwtVcX509SanDnsRequestUriSignedDirectPost(adapterUrl, conformanceHost, conformancePort)
            
            println("Creating test plan: ${plan.planName}")
            println("Variant: ${plan.variant}")
            
            val runner = WalletTestPlanRunner(plan, httpClient, conformanceHost, conformancePort)
            runner.test()
            
            println("=== Test Complete ===")
        } finally {
            httpClient.close()
        }
    }
}
