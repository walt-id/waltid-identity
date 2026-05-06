package id.walt.trust

import id.walt.trust.model.*
import id.walt.trust.service.DefaultTrustRegistryService
import id.walt.trust.store.InMemoryTrustStore
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import kotlin.time.Instant

/**
 * Integration tests for [DefaultTrustRegistryService].
 * Tests the full pipeline: load source → resolve trust decisions.
 */
class TrustRegistryServiceTest {

    private fun loadResource(name: String): String =
        this::class.java.classLoader.getResource(name)?.readText()
            ?: error("Test resource not found: $name")

    private fun createService(): DefaultTrustRegistryService {
        val store = InMemoryTrustStore()
        return DefaultTrustRegistryService(store)
    }

    // ---------------------------------------------------------------------------
    // Source loading
    // ---------------------------------------------------------------------------

    @Test
    fun `load LoTE JSON source successfully`() = runTest {
        val service = createService()
        val json = loadResource("sample-lote-wallet-providers.json")

        val result = service.loadSourceFromContent("demo-lote", json)

        assertTrue(result.success)
        assertEquals("demo-lote", result.sourceId)
        assertEquals(3, result.entitiesLoaded)
        assertEquals(3, result.servicesLoaded)
        assertTrue(result.identitiesLoaded >= 3)
    }

    @Test
    fun `load LoTE XML source successfully`() = runTest {
        val service = createService()
        val xml = loadResource("sample-lote-pid-providers.xml")

        val result = service.loadSourceFromContent("demo-lote-xml", xml)

        assertTrue(result.success)
        assertEquals(1, result.entitiesLoaded)
        assertEquals(1, result.servicesLoaded)
        assertEquals(1, result.identitiesLoaded)
    }

    @Test
    fun `load TSL XML source successfully`() = runTest {
        val service = createService()
        val xml = loadResource("sample-tl.xml")

        // Synthetic test fixture has no signature, so skip validation
        val result = service.loadSourceFromContent("demo-tsl", xml, validateSignature = false)

        assertTrue(result.success)
        assertEquals(1, result.entitiesLoaded)
        assertEquals(2, result.servicesLoaded)
        assertEquals(2, result.identitiesLoaded)
    }

    @Test
    fun `load unknown format fails gracefully`() = runTest {
        val service = createService()

        val result = service.loadSourceFromContent("bad", "this is not xml or json")

        assertFalse(result.success)
        assertNotNull(result.error)
    }

    // ---------------------------------------------------------------------------
    // Resolve by certificate SHA-256
    // ---------------------------------------------------------------------------

    @Test
    fun `resolve trusted certificate by SHA-256`() = runTest {
        val service = createService()
        val json = loadResource("sample-lote-wallet-providers.json")
        service.loadSourceFromContent("demo", json)

        val decision = service.resolveByCertificateSha256(
            sha256Hex = "9f3df3b70633c3d23f5ef04d5d1e7f1d715b9683d8744cd38ec1a8114ec99f00",
            instant = Instant.parse("2026-06-01T00:00:00Z")
        )

        assertEquals(TrustDecisionCode.TRUSTED, decision.decision)
        assertNotNull(decision.matchedEntity)
        assertEquals("AT-WALLET-001", decision.matchedEntity?.entityId)
        assertEquals(TrustedEntityType.WALLET_PROVIDER, decision.matchedEntity?.entityType)
        assertNotNull(decision.matchedService)
        assertEquals(TrustStatus.GRANTED, decision.matchedService?.status)
        assertTrue(decision.evidence.isNotEmpty())
    }

    @Test
    fun `resolve unknown certificate returns NOT_TRUSTED`() = runTest {
        val service = createService()
        val json = loadResource("sample-lote-wallet-providers.json")
        service.loadSourceFromContent("demo", json)

        val decision = service.resolveByCertificateSha256(
            sha256Hex = "0000000000000000000000000000000000000000000000000000000000000000",
            instant = Instant.parse("2026-06-01T00:00:00Z")
        )

        assertEquals(TrustDecisionCode.NOT_TRUSTED, decision.decision)
    }

    @Test
    fun `resolve suspended certificate returns NOT_TRUSTED`() = runTest {
        val service = createService()
        val json = loadResource("sample-lote-wallet-providers.json")
        service.loadSourceFromContent("demo", json)

        val decision = service.resolveByCertificateSha256(
            sha256Hex = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            instant = Instant.parse("2026-06-01T00:00:00Z")
        )

        assertEquals(TrustDecisionCode.NOT_TRUSTED, decision.decision)
    }

    @Test
    fun `resolve with entity type filter -- matching type`() = runTest {
        val service = createService()
        val json = loadResource("sample-lote-wallet-providers.json")
        service.loadSourceFromContent("demo", json)

        val decision = service.resolveByCertificateSha256(
            sha256Hex = "9f3df3b70633c3d23f5ef04d5d1e7f1d715b9683d8744cd38ec1a8114ec99f00",
            instant = Instant.parse("2026-06-01T00:00:00Z"),
            expectedEntityType = TrustedEntityType.WALLET_PROVIDER
        )

        assertEquals(TrustDecisionCode.TRUSTED, decision.decision)
    }

    @Test
    fun `resolve with entity type filter -- wrong type returns NOT_TRUSTED`() = runTest {
        val service = createService()
        val json = loadResource("sample-lote-wallet-providers.json")
        service.loadSourceFromContent("demo", json)

        val decision = service.resolveByCertificateSha256(
            sha256Hex = "9f3df3b70633c3d23f5ef04d5d1e7f1d715b9683d8744cd38ec1a8114ec99f00",
            instant = Instant.parse("2026-06-01T00:00:00Z"),
            expectedEntityType = TrustedEntityType.PID_PROVIDER
        )

        assertEquals(TrustDecisionCode.NOT_TRUSTED, decision.decision)
        assertTrue(decision.evidence.any { "TYPE_MISMATCH" in it.type })
    }

    @Test
    fun `resolve with SHA-256 prefix stripped`() = runTest {
        val service = createService()
        val json = loadResource("sample-lote-wallet-providers.json")
        service.loadSourceFromContent("demo", json)

        val decision = service.resolveByCertificateSha256(
            sha256Hex = "sha256:2f18886f6fd62dfd0f9015ec6b7b0af2870d3f6070c810f545f8d85f37eb8d11",
            instant = Instant.parse("2026-06-01T00:00:00Z")
        )

        assertEquals(TrustDecisionCode.TRUSTED, decision.decision)
        assertEquals("AT-PID-001", decision.matchedEntity?.entityId)
    }

    // ---------------------------------------------------------------------------
    // Resolve by provider ID
    // ---------------------------------------------------------------------------

    @Test
    fun `resolve by provider ID -- trusted entity`() = runTest {
        val service = createService()
        val json = loadResource("sample-lote-wallet-providers.json")
        service.loadSourceFromContent("demo", json)

        val decision = service.resolveByProviderId(
            providerId = "AT-WALLET-001",
            instant = Instant.parse("2026-06-01T00:00:00Z")
        )

        assertEquals(TrustDecisionCode.TRUSTED, decision.decision)
        assertNotNull(decision.matchedEntity)
        assertNotNull(decision.matchedService)
    }

    @Test
    fun `resolve by provider ID -- unknown entity`() = runTest {
        val service = createService()
        val json = loadResource("sample-lote-wallet-providers.json")
        service.loadSourceFromContent("demo", json)

        val decision = service.resolveByProviderId(
            providerId = "NONEXISTENT",
            instant = Instant.parse("2026-06-01T00:00:00Z")
        )

        assertEquals(TrustDecisionCode.NOT_TRUSTED, decision.decision)
    }

    @Test
    fun `resolve by provider ID -- type mismatch`() = runTest {
        val service = createService()
        val json = loadResource("sample-lote-wallet-providers.json")
        service.loadSourceFromContent("demo", json)

        val decision = service.resolveByProviderId(
            providerId = "AT-WALLET-001",
            instant = Instant.parse("2026-06-01T00:00:00Z"),
            expectedEntityType = TrustedEntityType.PID_PROVIDER
        )

        assertEquals(TrustDecisionCode.NOT_TRUSTED, decision.decision)
    }

    // ---------------------------------------------------------------------------
    // Freshness evaluation
    // ---------------------------------------------------------------------------

    @Test
    fun `expired source returns STALE_SOURCE decision`() = runTest {
        val service = createService()
        val json = loadResource("sample-lote-wallet-providers.json")
        service.loadSourceFromContent("demo", json)

        // Query at a time far after nextUpdate (2026-10-01)
        val decision = service.resolveByCertificateSha256(
            sha256Hex = "9f3df3b70633c3d23f5ef04d5d1e7f1d715b9683d8744cd38ec1a8114ec99f00",
            instant = Instant.parse("2027-01-01T00:00:00Z")
        )

        assertEquals(TrustDecisionCode.STALE_SOURCE, decision.decision)
        assertEquals(FreshnessState.EXPIRED, decision.sourceFreshness)
    }

    // ---------------------------------------------------------------------------
    // Source health and listing
    // ---------------------------------------------------------------------------

    @Test
    fun `list sources after loading`() = runTest {
        val service = createService()
        val json = loadResource("sample-lote-wallet-providers.json")
        val xml = loadResource("sample-tl.xml")
        service.loadSourceFromContent("lote-demo", json)
        // Synthetic test fixture has no signature, so skip validation
        service.loadSourceFromContent("tsl-demo", xml, validateSignature = false)

        val sources = service.listSources().toList()
        assertEquals(2, sources.size)
    }

    @Test
    fun `get source health`() = runTest {
        val service = createService()
        val json = loadResource("sample-lote-wallet-providers.json")
        service.loadSourceFromContent("demo", json)

        val health = service.getSourceHealth().toList()
        assertEquals(1, health.size)
        assertEquals("demo", health.first().sourceId)
        assertEquals(3, health.first().entityCount)
        assertEquals(3, health.first().serviceCount)
    }

    @Test
    fun `list trusted entities with filter`() = runTest {
        val service = createService()
        val json = loadResource("sample-lote-wallet-providers.json")
        service.loadSourceFromContent("demo", json)

        val all = service.listTrustedEntities().toList()
        assertEquals(3, all.size)

        val walletOnly = service.listTrustedEntities(EntityFilter(entityType = TrustedEntityType.WALLET_PROVIDER)).toList()
        assertEquals(2, walletOnly.size) // AT-WALLET-001 + DE-SUSPENDED-001

        val atOnly = service.listTrustedEntities(EntityFilter(country = "AT")).toList()
        assertEquals(2, atOnly.size) // AT-WALLET-001 + AT-PID-001
    }
}
