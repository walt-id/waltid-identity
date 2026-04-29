package id.walt.trust

import id.walt.trust.model.*
import id.walt.trust.store.InMemoryTrustStore
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Tests for [InMemoryTrustStore].
 */
class InMemoryTrustStoreTest {

    private fun createStore() = InMemoryTrustStore()

    private val testSource = TrustSource(
        sourceId = "src-1",
        sourceFamily = SourceFamily.LOTE,
        displayName = "Test Source"
    )

    private val testEntity = TrustedEntity(
        entityId = "entity-1",
        sourceId = "src-1",
        entityType = TrustedEntityType.WALLET_PROVIDER,
        legalName = "Test Wallet Provider"
    )

    private val testService = TrustedService(
        serviceId = "svc-1",
        sourceId = "src-1",
        entityId = "entity-1",
        serviceType = "WALLET_PROVIDER",
        status = TrustStatus.GRANTED
    )

    private val testIdentity = ServiceIdentity(
        identityId = "id-1",
        sourceId = "src-1",
        entityId = "entity-1",
        serviceId = "svc-1",
        certificateSha256Hex = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
    )

    @Test
    fun `upsert and retrieve source`() = runTest {
        val store = createStore()
        store.upsertSource(testSource)

        val result = store.getSource("src-1")
        assertNotNull(result)
        assertEquals("Test Source", result.displayName)
    }

    @Test
    fun `list sources`() = runTest {
        val store = createStore()
        store.upsertSource(testSource)
        store.upsertSource(testSource.copy(sourceId = "src-2", displayName = "Source 2"))

        val sources = store.listSources().toList()
        assertEquals(2, sources.size)
    }

    @Test
    fun `upsert and retrieve entity`() = runTest {
        val store = createStore()
        store.upsertEntities(listOf(testEntity))

        val result = store.getEntity("entity-1")
        assertNotNull(result)
        assertEquals("Test Wallet Provider", result.legalName)
    }

    @Test
    fun `upsert replaces existing entity`() = runTest {
        val store = createStore()
        store.upsertEntities(listOf(testEntity))
        store.upsertEntities(listOf(testEntity.copy(legalName = "Updated Name")))

        val result = store.getEntity("entity-1")
        assertNotNull(result)
        assertEquals("Updated Name", result.legalName)
    }

    @Test
    fun `find identities by certificate SHA-256`() = runTest {
        val store = createStore()
        store.upsertIdentities(listOf(testIdentity))

        val results = store.findIdentitiesByCertificateSha256(
            "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890"
        ).toList()
        assertEquals(1, results.size)
        assertEquals("id-1", results.first().identityId)
    }

    @Test
    fun `find identities by certificate SHA-256 is case insensitive`() = runTest {
        val store = createStore()
        store.upsertIdentities(listOf(testIdentity))

        val results = store.findIdentitiesByCertificateSha256(
            "ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890"
        ).toList()
        assertEquals(1, results.size)
    }

    @Test
    fun `find identities by subject DN`() = runTest {
        val store = createStore()
        val identityWithDn = testIdentity.copy(
            identityId = "id-dn",
            subjectDn = "CN=Test,O=Test Corp,C=AT"
        )
        store.upsertIdentities(listOf(identityWithDn))

        val results = store.findIdentitiesBySubjectDn("CN=Test,O=Test Corp,C=AT").toList()
        assertEquals(1, results.size)
    }

    @Test
    fun `find identities by SKI`() = runTest {
        val store = createStore()
        val identityWithSki = testIdentity.copy(
            identityId = "id-ski",
            subjectKeyIdentifierHex = "aa11bb22cc33"
        )
        store.upsertIdentities(listOf(identityWithSki))

        val results = store.findIdentitiesBySkiHex("AA11BB22CC33").toList()
        assertEquals(1, results.size)
    }

    @Test
    fun `find identities by issuer and serial`() = runTest {
        val store = createStore()
        val identityWithIssuerSerial = testIdentity.copy(
            identityId = "id-is",
            issuerDn = "CN=Issuer CA,C=AT",
            serialNumber = "12345"
        )
        store.upsertIdentities(listOf(identityWithIssuerSerial))

        val results = store.findIdentitiesByIssuerAndSerial("CN=Issuer CA,C=AT", "12345").toList()
        assertEquals(1, results.size)
    }

    @Test
    fun `list entities with filter by type`() = runTest {
        val store = createStore()
        val pidEntity = testEntity.copy(
            entityId = "pid-1",
            entityType = TrustedEntityType.PID_PROVIDER,
            legalName = "PID Provider"
        )
        store.upsertEntities(listOf(testEntity, pidEntity))

        val walletProviders = store.listEntities(EntityFilter(entityType = TrustedEntityType.WALLET_PROVIDER)).toList()
        assertEquals(1, walletProviders.size)
        assertEquals("entity-1", walletProviders.first().entityId)

        val pidProviders = store.listEntities(EntityFilter(entityType = TrustedEntityType.PID_PROVIDER)).toList()
        assertEquals(1, pidProviders.size)
        assertEquals("pid-1", pidProviders.first().entityId)
    }

    @Test
    fun `list entities with filter by country`() = runTest {
        val store = createStore()
        val atEntity = testEntity.copy(country = "AT")
        val deEntity = testEntity.copy(entityId = "entity-de", country = "DE")
        store.upsertEntities(listOf(atEntity, deEntity))

        val atResults = store.listEntities(EntityFilter(country = "AT")).toList()
        assertEquals(1, atResults.size)
    }

    @Test
    fun `list entities -- onlyCurrentlyTrusted filters by service status`() = runTest {
        val store = createStore()
        val grantedEntity = testEntity.copy(entityId = "granted-e")
        val revokedEntity = testEntity.copy(entityId = "revoked-e")
        store.upsertEntities(listOf(grantedEntity, revokedEntity))
        store.upsertServices(
            listOf(
                testService.copy(serviceId = "svc-granted", entityId = "granted-e", status = TrustStatus.GRANTED),
                testService.copy(serviceId = "svc-revoked", entityId = "revoked-e", status = TrustStatus.REVOKED)
            )
        )

        val trusted = store.listEntities(EntityFilter(onlyCurrentlyTrusted = true)).toList()
        assertEquals(1, trusted.size)
        assertEquals("granted-e", trusted.first().entityId)
    }

    @Test
    fun `update source freshness`() = runTest {
        val store = createStore()
        store.upsertSource(testSource)

        store.updateSourceFreshness("src-1", FreshnessState.STALE)
        val result = store.getSource("src-1")
        assertEquals(FreshnessState.STALE, result?.freshnessState)
    }

    @Test
    fun `update source authenticity`() = runTest {
        val store = createStore()
        store.upsertSource(testSource)

        store.updateSourceAuthenticity("src-1", AuthenticityState.VALIDATED)
        val result = store.getSource("src-1")
        assertEquals(AuthenticityState.VALIDATED, result?.authenticityState)
    }

    @Test
    fun `list services for entity`() = runTest {
        val store = createStore()
        store.upsertServices(
            listOf(
                testService,
                testService.copy(serviceId = "svc-2", entityId = "entity-1"),
                testService.copy(serviceId = "svc-other", entityId = "entity-other")
            )
        )

        val services = store.listServicesForEntity("entity-1").toList()
        assertEquals(2, services.size)
    }

    @Test
    fun `count entities and services for source`() = runTest {
        val store = createStore()
        store.upsertEntities(
            listOf(
                testEntity,
                testEntity.copy(entityId = "entity-2")
            )
        )
        store.upsertServices(
            listOf(
                testService,
                testService.copy(serviceId = "svc-2"),
                testService.copy(serviceId = "svc-3")
            )
        )

        assertEquals(2, store.countEntities("src-1"))
        assertEquals(3, store.countServices("src-1"))
    }
}
