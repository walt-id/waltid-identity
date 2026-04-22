package id.walt.trust.service

import id.walt.trust.model.*
import id.walt.trust.store.InMemoryTrustStore
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for loading trust sources from URLs.
 * These tests require network access and are disabled by default.
 * Enable with: RUN_NETWORK_TESTS=true
 */
class LoadFromUrlIntegrationTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_NETWORK_TESTS", matches = "true")
    fun `load German TSL from URL with signature validation`() = runBlocking {
        val store = InMemoryTrustStore()
        val service = DefaultTrustRegistryService(store)
        
        val result = service.loadSourceFromUrl(
            sourceId = "de-tsl",
            url = "https://www.nrca-ds.de/st/TSL-XML.xml",
            validateSignature = true
        )
        
        println("=== German TSL Load Result ===")
        println("Success: ${result.success}")
        println("Entities: ${result.entitiesLoaded}")
        println("Services: ${result.servicesLoaded}")
        println("Identities: ${result.identitiesLoaded}")
        result.error?.let { println("Error: $it") }
        
        assertTrue(result.success, "Should successfully load German TSL")
        assertTrue(result.entitiesLoaded > 0, "Should have entities")
        
        // Check source metadata
        val sources = service.listSources()
        val deSource = sources.find { it.sourceId == "de-tsl" }
        assertNotNull(deSource)
        assertEquals("DE", deSource.territory)
        assertEquals(SourceFamily.TSL, deSource.sourceFamily)
        assertEquals(AuthenticityState.VALIDATED, deSource.authenticityState, "Signature should be validated")
        
        println("Territory: ${deSource.territory}")
        println("Authenticity: ${deSource.authenticityState}")
        println("Freshness: ${deSource.freshnessState}")
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_NETWORK_TESTS", matches = "true")
    fun `load EU LoTL from URL with signature validation`() = runBlocking {
        val store = InMemoryTrustStore()
        val service = DefaultTrustRegistryService(store)
        
        val result = service.loadSourceFromUrl(
            sourceId = "eu-lotl",
            url = "https://ec.europa.eu/tools/lotl/eu-lotl.xml",
            validateSignature = true
        )
        
        println("=== EU LoTL Load Result ===")
        println("Success: ${result.success}")
        println("Entities: ${result.entitiesLoaded}")
        println("Services: ${result.servicesLoaded}")
        println("Identities: ${result.identitiesLoaded}")
        result.error?.let { println("Error: $it") }
        
        assertTrue(result.success, "Should successfully load EU LoTL")
        
        // Check source metadata
        val sources = service.listSources()
        val euSource = sources.find { it.sourceId == "eu-lotl" }
        assertNotNull(euSource)
        assertEquals("EU", euSource.territory)
        assertEquals(AuthenticityState.VALIDATED, euSource.authenticityState, "Signature should be validated")
        
        println("Territory: ${euSource.territory}")
        println("Authenticity: ${euSource.authenticityState}")
    }

    @Test
    fun `interface compiles and methods are callable`() {
        // This test ensures the API compiles correctly.
        // Network tests are enabled via RUN_NETWORK_TESTS=true
        val store = InMemoryTrustStore()
        val service = DefaultTrustRegistryService(store)
        
        // Verify the method signature exists
        runBlocking {
            val result = service.loadSourceFromUrl(
                sourceId = "test",
                url = "https://example.invalid", // intentionally invalid
                validateSignature = false
            )
            // Expected to fail (can't connect), but proves the method works
            assertTrue(!result.success, "Should fail to connect to invalid URL")
            assertTrue(result.error != null, "Should have error message")
            println("API test passed - error as expected: ${result.error}")
        }
    }
}
