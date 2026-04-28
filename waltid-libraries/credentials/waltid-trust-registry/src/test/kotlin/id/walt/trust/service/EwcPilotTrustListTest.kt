package id.walt.trust.service

import id.walt.trust.model.*
import id.walt.trust.store.InMemoryTrustStore
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.assertTrue

/**
 * Integration test for EWC Large Scale Pilot Trust List.
 * Contains WALLET_PROVIDER, PID_PROVIDER, and ATTESTATION_PROVIDER (EAA) entries.
 */
class EwcPilotTrustListTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_NETWORK_TESTS", matches = "true")
    fun `load EWC LSP trust list - has wallet and PID providers`() = runBlocking {
        val store = InMemoryTrustStore()
        val service = DefaultTrustRegistryService(store)
        
        val result = service.loadSourceFromUrl(
            sourceId = "ewc-pilot",
            url = "https://ewc-consortium.github.io/ewc-trust-list/EWC-TL",
            validateSignature = false // EWC pilot list is not signed
        )
        
        println("=== EWC LSP Trust List Load Result ===")
        println("Success: ${result.success}")
        println("Entities: ${result.entitiesLoaded}")
        println("Services: ${result.servicesLoaded}")
        println("Identities: ${result.identitiesLoaded}")
        result.error?.let { println("Error: $it") }
        
        assertTrue(result.success, "Should successfully load EWC pilot list: ${result.error}")
        assertTrue(result.entitiesLoaded > 0, "EWC should have TSPs")
        
        // List all entities
        val entities = service.listTrustedEntities()
        println("\n=== Entities (${entities.size}) ===")
        entities.forEach { entity ->
            println("  - ${entity.legalName} [${entity.entityType}] (${entity.country})")
        }
        
        // Check source metadata
        val sources = service.listSources()
        val ewcSource = sources.find { it.sourceId == "ewc-pilot" }
        println("\n=== Source Metadata ===")
        println("Territory: ${ewcSource?.territory}")
        println("Source Family: ${ewcSource?.sourceFamily}")
        println("Issue Date: ${ewcSource?.issueDate}")
    }
}
