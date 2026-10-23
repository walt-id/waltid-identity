package id.walt.trust.service

import id.walt.trust.fetcher.FetcherSecurityConfig
import id.walt.trust.fetcher.SourceFetcher
import id.walt.trust.model.AuthenticityState
import id.walt.trust.model.SourceFamily
import id.walt.trust.model.SourceLoadErrorCode
import id.walt.trust.store.InMemoryTrustStore
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.parallel.Isolated
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for loading trust sources from URLs.
 * Provider tests require network access: RUN_NETWORK_TESTS=true.
 * The API failure case uses a local host policy and needs no network.
 */
@Isolated("Temporarily changes SourceFetcher.securityConfig")
class LoadFromUrlIntegrationTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_NETWORK_TESTS", matches = "true")
    fun `load Austrian TSL from URL with signature validation`() = runBlocking {
        val store = InMemoryTrustStore()
        val service = DefaultTrustRegistryService(store)

        val result = service.loadSourceFromUrl(
            sourceId = "at-tsl",
            url = "https://www.signatur.rtr.at/vertrauensliste.xml",
            validateSignature = true
        )

        println("=== Austrian TSL Load Result ===")
        println("Success: ${result.success}")
        println("Entities: ${result.entitiesLoaded}")
        println("Services: ${result.servicesLoaded}")
        println("Identities: ${result.identitiesLoaded}")
        result.error?.let { println("Error: $it") }

        assertTrue(result.success, "Should successfully load Austrian TSL: ${result.error}")
        assertTrue(result.entitiesLoaded > 0, "Should have entities")
        assertTrue(result.servicesLoaded > 0, "Should have services")

        // Check source metadata
        val sources = service.listSources().toList()
        val atSource = sources.find { it.sourceId == "at-tsl" }
        assertNotNull(atSource)
        assertEquals("AT", atSource.territory)
        assertEquals(SourceFamily.TSL, atSource.sourceFamily)
        assertEquals(AuthenticityState.INTEGRITY_VERIFIED, atSource.assurance.authenticityState, "Signature should be validated")

        println("Territory: ${atSource.territory}")
        println("Authenticity: ${atSource.assurance.authenticityState}")
        println("Freshness: ${atSource.freshnessState}")

        // List some entities
        val entities = service.listTrustedEntities().toList()
        println("\n=== Sample Entities ===")
        entities.take(5).forEach { entity ->
            println("  - ${entity.legalName} (${entity.entityType})")
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_NETWORK_TESTS", matches = "true")
    fun `load Italian TSL from URL with signature validation`() = runBlocking {
        val store = InMemoryTrustStore()
        val service = DefaultTrustRegistryService(store)

        val result = service.loadSourceFromUrl(
            sourceId = "it-tsl",
            url = "https://eidas.agid.gov.it/TL/TSL-IT.xml",
            validateSignature = true
        )

        println("=== Italian TSL Load Result ===")
        println("Success: ${result.success}")
        println("Entities: ${result.entitiesLoaded}")
        println("Services: ${result.servicesLoaded}")
        println("Identities: ${result.identitiesLoaded}")
        result.error?.let { println("Error: $it") }

        assertTrue(result.success, "Should successfully load Italian TSL: ${result.error}")
        assertTrue(result.entitiesLoaded > 10, "Italy should have many TSPs")
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_NETWORK_TESTS", matches = "true")
    fun `load EU LoTL from URL - expect 0 entities (pointers only)`() = runBlocking {
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
        println("Note: EU LoTL contains pointers to member state TSLs, not actual TSPs")

        assertTrue(result.success, "Should successfully load EU LoTL")
        assertEquals(0, result.entitiesLoaded, "LoTL should have 0 direct entities (it has pointers)")

        // Check signature is validated
        val sources = service.listSources().toList()
        val euSource = sources.find { it.sourceId == "eu-lotl" }
        assertNotNull(euSource)
        assertEquals(AuthenticityState.INTEGRITY_VERIFIED, euSource.assurance.authenticityState)
    }

    @Test
    fun `interface compiles and methods are callable`() {
        val service = DefaultTrustRegistryService(InMemoryTrustStore())
        val previousConfig = SourceFetcher.securityConfig
        try {
            SourceFetcher.securityConfig = FetcherSecurityConfig(allowedHosts = setOf("audit-only.invalid"))
            runBlocking {
                val result = service.loadSourceFromUrl(
                    sourceId = "test",
                    url = "https://example.invalid",
                    validateSignature = false
                )
                assertTrue(!result.success, "Should reject a host outside the allow-list")
                assertTrue(result.error != null, "Should have error message")
                assertEquals("test", result.sourceId)
                assertEquals(SourceLoadErrorCode.FETCH_FAILED, result.errorCode)
                assertEquals(
                    "URL validation failed: Host 'example.invalid' not in allowed hosts list",
                    result.error
                )
            }
        } finally {
            SourceFetcher.securityConfig = previousConfig
        }
    }
}
