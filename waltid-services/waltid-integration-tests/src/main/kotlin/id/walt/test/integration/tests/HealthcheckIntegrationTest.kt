package id.walt.test.integration.tests

import id.walt.test.integration.expectSuccess
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for healthcheck endpoints across all services.
 * Tests the /livez endpoint for wallet, issuer, and verifier APIs.
 */
class HealthcheckIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun shouldReturnHealthyStatusForWalletApi() = runTest {
        val client = environment.testHttpClient()
        val response = client.get("/livez")
        response.expectSuccess()
        
        val healthChecks = response.body<JsonArray>()
        assertTrue(healthChecks.isNotEmpty(), "Health check response should not be empty")
        
        healthChecks.forEach { check ->
            val status = check.jsonObject["status"]?.jsonPrimitive?.content
            assertEquals("Healthy", status, "All health checks should be healthy")
        }
    }

    @Test
    fun shouldReturnHealthyStatusForIssuerApi() = runTest {
        val client = environment.testHttpClient()
        val response = client.get("/livez")
        response.expectSuccess()
        
        val healthChecks = response.body<JsonArray>()
        assertTrue(healthChecks.isNotEmpty(), "Health check response should not be empty")
        
        val hasHealthyStatus = healthChecks.any { check ->
            check.jsonObject["status"]?.jsonPrimitive?.content == "Healthy"
        }
        assertTrue(hasHealthyStatus, "At least one health check should be healthy")
    }

    @Test
    fun shouldReturnHealthyStatusForVerifierApi() = runTest {
        val client = environment.testHttpClient()
        val response = client.get("/livez")
        response.expectSuccess()
        
        val healthChecks = response.body<JsonArray>()
        assertTrue(healthChecks.isNotEmpty(), "Health check response should not be empty")
        
        val hasHealthyStatus = healthChecks.any { check ->
            check.jsonObject["status"]?.jsonPrimitive?.content == "Healthy"
        }
        assertTrue(hasHealthyStatus, "At least one health check should be healthy")
    }
}
