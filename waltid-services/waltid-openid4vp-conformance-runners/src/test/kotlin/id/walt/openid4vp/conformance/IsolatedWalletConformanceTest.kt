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
import kotlinx.serialization.json.buildJsonObject
import org.junit.jupiter.api.condition.EnabledIf
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Isolated Wallet Conformance Test
 * 
 * This test validates that we can successfully create test plans and retrieve modules
 * from the conformance suite. It does NOT run the full test flow (which requires
 * the wallet adapter and wallet API to be running).
 * 
 * ## Running
 * 
 * ```bash
 * ./gradlew :waltid-services:waltid-openid4vp-conformance-runners:test \
 *     --tests "IsolatedWalletConformanceTest"
 * ```
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

    /**
     * Test that we can create a test plan and get modules from the response.
     * 
     * This validates:
     * - SSL/TLS connection to conformance suite works
     * - Test plan creation API works
     * - Modules are returned in the create response
     */
    @Test
    @EnabledIf("isConformanceAvailable")
    fun testCreatePlanAndGetModules() = runBlocking {
        println("=== Test Plan Creation Test ===")
        
        val conformance = ConformanceInterface(conformanceHost, conformancePort)
        val adapterUrl = "http://host.docker.internal:${ConformanceConfig.WALLET_ADAPTER_PORT}/openid4vp/authorize"
        val plan = VpWalletSdJwtVcX509SanDnsRequestUriSignedDirectPost(adapterUrl, conformanceHost, conformancePort)
        
        println("Creating test plan: ${plan.planName}")
        println("Variant: ${plan.variant}")
        
        // Create test plan URL
        val variantJson = Json.encodeToString(plan.variant)
        val createTestPlanUrl = conformance.createTestPlanUrlWithConfig {
            append("planName", plan.planName)
            append("variant", variantJson)
        }
        
        // Create test plan
        val body = buildJsonObject {
            put("configuration", plan.configuration)
        }
        val response = conformance.createTestPlan(createTestPlanUrl, body)
        
        println("Created test plan: ${response.id}")
        println("Modules: ${response.modules.size}")
        response.modules.forEach { module ->
            println("  - ${module.testModule}")
        }
        
        // Assertions
        assertTrue(response.id.isNotEmpty(), "Test plan ID should not be empty")
        assertTrue(response.modules.isNotEmpty(), "Should have at least one test module")
        assertTrue(response.modules.any { it.testModule.contains("happy-flow") }, 
            "Should include happy-flow test module")
        
        println("=== Test Plan Creation Successful ===")
    }
}
