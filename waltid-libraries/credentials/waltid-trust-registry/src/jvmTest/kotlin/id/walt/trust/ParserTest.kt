package id.walt.trust

import id.walt.trust.model.TrustStatus
import id.walt.trust.model.SourceFamily
import id.walt.trust.model.TrustedEntityType
import id.walt.trust.parser.tsl.TslParseConfig
import id.walt.trust.parser.tsl.TslXmlParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Contract tests for ETSI TS 119 612 TSL XML.
 */
class ParserTest {

    private fun loadResource(name: String): String =
        this::class.java.classLoader.getResource(name)?.readText()
            ?: error("Test resource not found: $name")

    /** Config for synthetic test fixtures that have no XMLDSig signature */
    private val unsignedTslConfig = TslParseConfig(
        validateSignature = false
    )

    // ---------------------------------------------------------------------------
    // TSL XML parser
    // ---------------------------------------------------------------------------

    @Test
    fun `TSL XML parser -- parses Austrian TL`() {
        val xml = loadResource("sample-tl.xml")
        val result = TslXmlParser.parse(xml, "test-tsl", config = unsignedTslConfig)

        assertEquals("test-tsl", result.source.sourceId)
        assertEquals(SourceFamily.TSL, result.source.sourceFamily)
        assertEquals("AT", result.source.territory)
        assertNotNull(result.source.issueDate)
        assertNotNull(result.source.nextUpdate)

        // One TSP with two services
        assertEquals(1, result.entities.size)
        assertEquals(2, result.services.size)
        assertEquals(2, result.identities.size) // one identity per service

        val tsp = result.entities.first()
        assertEquals(TrustedEntityType.TRUST_SERVICE_PROVIDER, tsp.entityType)
        assertEquals("Demo QTSP Austria", tsp.legalName)
        assertEquals("AT", tsp.country)
    }

    @Test
    fun `TSL XML parser -- maps service statuses correctly`() {
        val xml = loadResource("sample-tl.xml")
        val result = TslXmlParser.parse(xml, "test-tsl", config = unsignedTslConfig)

        val grantedService = result.services.find { "CA/QC" in it.serviceType }
        assertNotNull(grantedService)
        assertEquals(TrustStatus.GRANTED, grantedService.status)

        val suspendedService = result.services.find { "TSA/QTST" in it.serviceType }
        assertNotNull(suspendedService)
        assertEquals(TrustStatus.SUSPENDED, suspendedService.status)
    }

    @Test
    fun `TSL XML parser -- extracts subject DN from digital identity`() {
        val xml = loadResource("sample-tl.xml")
        val result = TslXmlParser.parse(xml, "test-tsl", config = unsignedTslConfig)

        val dnIdentities = result.identities.filter { it.subjectDn != null }
        assertTrue(dnIdentities.isNotEmpty(), "Should extract at least one subject DN")
        assertTrue(
            dnIdentities.any { it.subjectDn?.contains("Demo QTSP") == true },
            "Should find Demo QTSP subject DN"
        )
    }

    @Test
    fun `TSL XML parser -- preserves raw status URI in metadata`() {
        val xml = loadResource("sample-tl.xml")
        val result = TslXmlParser.parse(xml, "test-tsl", config = unsignedTslConfig)

        val grantedService = result.services.find { it.status == TrustStatus.GRANTED }
        assertNotNull(grantedService)
        assertTrue(
            grantedService.metadata["rawStatusUri"]?.contains("granted") == true,
            "Should preserve raw status URI"
        )
    }
}
