package id.walt.trust.parser

import id.walt.trust.model.AuthenticityState
import id.walt.trust.parser.tsl.TslParseConfig
import id.walt.trust.parser.tsl.TslXmlParser
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test parsing the South African DFID Trust List sample.
 * This is a non-EU TSL with custom extensions for DFID (Digital Financial Identity).
 */
class ZaSampleTslTest {

    @Test
    fun `parse ZA DFID trust list sample`() {
        val sampleFile = File("/home/pp/dev/walt-id/waltid-architecture/enterprise/trust-lists/samples/trust_list_structure_xml.xml")
        if (!sampleFile.exists()) {
            println("Sample file not found, skipping test")
            return
        }

        val xmlContent = sampleFile.readText()

        // Parse without signature validation (no XMLDSig in sample)
        val config = TslParseConfig(
            validateSignature = false
        )

        val result = TslXmlParser.parse(
            xml = xmlContent,
            sourceId = "za-dfid-sample",
            sourceUrl = "file:///sample/trust_list_structure_xml.xml",
            config = config
        )

        println("=== ZA DFID Trust List Parse Result ===")
        println("Source ID: ${result.source.sourceId}")
        println("Display Name: ${result.source.displayName}")
        println("Territory: ${result.source.territory}")
        println("Issue Date: ${result.source.issueDate}")
        println("Next Update: ${result.source.nextUpdate}")
        println("Sequence: ${result.source.sequenceNumber}")
        println("Freshness: ${result.source.freshnessState}")
        println("Authenticity: ${result.source.authenticityState}")
        println("Metadata: ${result.source.metadata}")
        println()

        println("=== Entities (${result.entities.size}) ===")
        result.entities.forEachIndexed { idx, entity ->
            println("\n[$idx] ${entity.legalName}")
            println("    Entity ID: ${entity.entityId}")
            println("    Entity Type: ${entity.entityType}")
            println("    Country: ${entity.country}")
        }

        println("\n=== Services (${result.services.size}) ===")
        result.services.forEachIndexed { idx, service ->
            println("\n[$idx] Service ID: ${service.serviceId}")
            println("    Type: ${service.serviceType}")
            println("    Status: ${service.status}")
            println("    Status Start: ${service.statusStart}")
            println("    Metadata: ${service.metadata}")
        }

        println("\n=== Identities (${result.identities.size}) ===")
        result.identities.forEachIndexed { idx, identity ->
            println("\n[$idx] Identity ID: ${identity.identityId}")
            println("    Cert SHA256: ${identity.certificateSha256Hex?.take(32)}...")
            println("    Subject DN: ${identity.subjectDn}")
            println("    SKI: ${identity.subjectKeyIdentifierHex}")
        }

        // Assertions
        assertEquals("ZA", result.source.territory)
        assertEquals(AuthenticityState.SKIPPED_DEMO, result.source.authenticityState, "Without validation, should be SKIPPED_DEMO")
        assertTrue(result.entities.isNotEmpty(), "Should have entities")

        // Check for expected TSPs
        val tspNames = result.entities.map { it.legalName }
        println("\n=== All TSP Names ===")
        tspNames.forEach { println("  - $it") }

        assertTrue(tspNames.contains("Department of Home Affairs"), "Should have DHA")
        assertTrue(tspNames.contains("Example Bank Ltd"), "Should have Example Bank")

        // Check services
        val allServiceTypes = result.services.map { it.serviceType }.distinct()
        println("\n=== All Service Types ===")
        allServiceTypes.forEach { println("  - $it") }

        // Should have various service types
        assertTrue(result.services.isNotEmpty(), "Should have services")

        // Check identities (x509 certs)
        assertTrue(result.identities.isNotEmpty(), "Should have identities")

        println("\n=== Test PASSED ===")
    }
}
