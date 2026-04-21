package id.walt.trust

import id.walt.trust.model.*
import id.walt.trust.parser.lote.LoteJsonParser
import id.walt.trust.parser.lote.LoteXmlParser
import id.walt.trust.parser.tsl.TslXmlParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Contract tests for all three parsers: LoTE JSON, LoTE XML, TSL XML.
 * Uses synthetic sample fixtures from test resources.
 */
class ParserTest {

    private fun loadResource(name: String): String =
        this::class.java.classLoader.getResource(name)?.readText()
            ?: error("Test resource not found: $name")

    // ---------------------------------------------------------------------------
    // LoTE JSON parser
    // ---------------------------------------------------------------------------

    @Test
    fun `LoTE JSON parser -- parses wallet and PID providers`() {
        val json = loadResource("sample-lote-wallet-providers.json")
        val result = LoteJsonParser.parse(json, "test-lote-json")

        assertEquals("test-lote-json", result.source.sourceId)
        assertEquals(SourceFamily.LOTE, result.source.sourceFamily)
        assertEquals("EU", result.source.territory)
        assertNotNull(result.source.issueDate)
        assertNotNull(result.source.nextUpdate)

        // 3 entities: AT-WALLET-001, AT-PID-001, DE-SUSPENDED-001
        assertEquals(3, result.entities.size)
        assertEquals(3, result.services.size)
        assertTrue(result.identities.size >= 3) // at least one per service

        // Check AT-WALLET-001
        val walletEntity = result.entities.find { it.entityId == "AT-WALLET-001" }
        assertNotNull(walletEntity)
        assertEquals(TrustedEntityType.WALLET_PROVIDER, walletEntity.entityType)
        assertEquals("Demo Wallet Provider GmbH", walletEntity.legalName)
        assertEquals("AT", walletEntity.country)

        // Check PID provider
        val pidEntity = result.entities.find { it.entityId == "AT-PID-001" }
        assertNotNull(pidEntity)
        assertEquals(TrustedEntityType.PID_PROVIDER, pidEntity.entityType)

        // Check suspended entity
        val suspendedEntity = result.entities.find { it.entityId == "DE-SUSPENDED-001" }
        assertNotNull(suspendedEntity)
        val suspendedService = result.services.find { it.entityId == "DE-SUSPENDED-001" }
        assertNotNull(suspendedService)
        assertEquals(TrustStatus.SUSPENDED, suspendedService.status)
    }

    @Test
    fun `LoTE JSON parser -- extracts certificate SHA-256 identities`() {
        val json = loadResource("sample-lote-wallet-providers.json")
        val result = LoteJsonParser.parse(json, "test-lote-json")

        val sha256Identities = result.identities.filter { it.certificateSha256Hex != null }
        assertTrue(sha256Identities.isNotEmpty(), "Should find at least one SHA-256 identity")

        // Check the wallet provider certificate fingerprint
        val walletCert = sha256Identities.find {
            it.certificateSha256Hex == "9f3df3b70633c3d23f5ef04d5d1e7f1d715b9683d8744cd38ec1a8114ec99f00"
        }
        assertNotNull(walletCert, "Should find wallet provider certificate")
        assertEquals("AT-WALLET-001", walletCert.entityId)
    }

    @Test
    fun `LoTE JSON parser -- extracts subject DN identities`() {
        val json = loadResource("sample-lote-wallet-providers.json")
        val result = LoteJsonParser.parse(json, "test-lote-json")

        val dnIdentities = result.identities.filter { it.subjectDn != null }
        assertTrue(dnIdentities.isNotEmpty(), "Should find at least one DN identity")

        val walletDn = dnIdentities.find {
            it.subjectDn == "CN=Demo Wallet Provider,O=Demo Wallet Provider GmbH,C=AT"
        }
        assertNotNull(walletDn, "Should find wallet provider DN")
    }

    @Test
    fun `LoTE JSON parser -- service IDs are scoped to entity`() {
        val json = loadResource("sample-lote-wallet-providers.json")
        val result = LoteJsonParser.parse(json, "test-lote-json")

        // All service IDs should contain their entity ID as prefix
        result.services.forEach { svc ->
            assertTrue(svc.serviceId.startsWith(svc.entityId), "Service ID should be scoped: ${svc.serviceId}")
        }
    }

    // ---------------------------------------------------------------------------
    // LoTE XML parser
    // ---------------------------------------------------------------------------

    @Test
    fun `LoTE XML parser -- parses PID provider`() {
        val xml = loadResource("sample-lote-pid-providers.xml")
        val result = LoteXmlParser.parse(xml, "test-lote-xml")

        assertEquals("test-lote-xml", result.source.sourceId)
        assertEquals(SourceFamily.LOTE, result.source.sourceFamily)
        assertEquals("EU", result.source.territory)
        assertEquals("eu-pid-providers-demo", result.source.displayName)

        assertEquals(1, result.entities.size)
        assertEquals(1, result.services.size)
        assertEquals(1, result.identities.size)

        val entity = result.entities.first()
        assertEquals("AT-PID-001", entity.entityId)
        assertEquals(TrustedEntityType.PID_PROVIDER, entity.entityType)
        assertEquals("Demo PID Provider Austria", entity.legalName)
        assertEquals("AT", entity.country)

        val service = result.services.first()
        assertEquals(TrustStatus.GRANTED, service.status)
        assertEquals("PID_PROVIDER_ACCESS", service.serviceType)

        val identity = result.identities.first()
        assertEquals(
            "2f18886f6fd62dfd0f9015ec6b7b0af2870d3f6070c810f545f8d85f37eb8d11",
            identity.certificateSha256Hex
        )
    }

    // ---------------------------------------------------------------------------
    // TSL XML parser
    // ---------------------------------------------------------------------------

    @Test
    fun `TSL XML parser -- parses Austrian TL`() {
        val xml = loadResource("sample-tl.xml")
        val result = TslXmlParser.parse(xml, "test-tsl")

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
        val result = TslXmlParser.parse(xml, "test-tsl")

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
        val result = TslXmlParser.parse(xml, "test-tsl")

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
        val result = TslXmlParser.parse(xml, "test-tsl")

        val grantedService = result.services.find { it.status == TrustStatus.GRANTED }
        assertNotNull(grantedService)
        assertTrue(
            grantedService.metadata["rawStatusUri"]?.contains("granted") == true,
            "Should preserve raw status URI"
        )
    }
}
