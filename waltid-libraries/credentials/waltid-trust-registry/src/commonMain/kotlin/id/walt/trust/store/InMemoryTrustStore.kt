package id.walt.trust.store

import id.walt.trust.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe in-memory implementation of [TrustStore].
 * Suitable for MVP / demo use. Not persistent across restarts.
 *
 * Flow-returning methods snapshot the relevant collection under the mutex and
 * emit from the snapshot, so the lock is not held during downstream consumption.
 */
class InMemoryTrustStore : TrustStore {

    private val mutex = Mutex()

    private val sources = mutableMapOf<String, TrustSource>()
    private val entities = mutableMapOf<String, TrustedEntity>()
    private val services = mutableMapOf<String, TrustedService>()
    private val identities = mutableMapOf<String, ServiceIdentity>()

    // ---------------------------------------------------------------------------
    // Write operations
    // ---------------------------------------------------------------------------

    override suspend fun upsertSource(source: TrustSource) = mutex.withLock {
        sources[source.sourceId] = source
    }

    override suspend fun upsertEntities(entities: List<TrustedEntity>) = mutex.withLock {
        entities.forEach { this.entities[it.entityId] = it }
    }

    override suspend fun upsertServices(services: List<TrustedService>) = mutex.withLock {
        services.forEach { this.services[it.serviceId] = it }
    }

    override suspend fun upsertIdentities(identities: List<ServiceIdentity>) = mutex.withLock {
        identities.forEach { this.identities[it.identityId] = it }
    }

    override suspend fun replaceSourceData(
        source: TrustSource,
        entities: List<TrustedEntity>,
        services: List<TrustedService>,
        identities: List<ServiceIdentity>
    ) = mutex.withLock {
        val sourceId = source.sourceId

        // Remove existing data for this source (removeIf is JVM-only; use iterator-based removal for KMP)
        val entityKeys = this.entities.entries.filter { it.value.sourceId == sourceId }.map { it.key }
        entityKeys.forEach { this.entities.remove(it) }
        val serviceKeys = this.services.entries.filter { it.value.sourceId == sourceId }.map { it.key }
        serviceKeys.forEach { this.services.remove(it) }
        val identityKeys = this.identities.entries.filter { it.value.sourceId == sourceId }.map { it.key }
        identityKeys.forEach { this.identities.remove(it) }

        // Insert new data atomically (within same lock)
        this.sources[sourceId] = source
        entities.forEach { this.entities[it.entityId] = it }
        services.forEach { this.services[it.serviceId] = it }
        identities.forEach { this.identities[it.identityId] = it }
    }

    override suspend fun updateSourceFreshness(sourceId: String, freshnessState: FreshnessState): Unit = mutex.withLock {
        sources[sourceId]?.let { sources[sourceId] = it.copy(freshnessState = freshnessState) }
        Unit
    }

    override suspend fun updateSourceAuthenticity(sourceId: String, authenticityState: AuthenticityState): Unit = mutex.withLock {
        sources[sourceId]?.let { sources[sourceId] = it.copy(authenticityState = authenticityState) }
        Unit
    }

    // ---------------------------------------------------------------------------
    // Source queries
    // ---------------------------------------------------------------------------

    override suspend fun getSource(sourceId: String): TrustSource? = mutex.withLock {
        sources[sourceId]
    }

    override suspend fun listSources(): Flow<TrustSource> =
        snapshotFlow { sources.values.toList() }

    // ---------------------------------------------------------------------------
    // Identity lookups
    // ---------------------------------------------------------------------------

    override suspend fun findIdentitiesByCertificateSha256(sha256Hex: String): Flow<ServiceIdentity> =
        snapshotFlow {
            identities.values.filter {
                it.certificateSha256Hex?.equals(sha256Hex, ignoreCase = true) == true
            }
        }

    override suspend fun findIdentitiesBySubjectDn(subjectDn: String): Flow<ServiceIdentity> =
        snapshotFlow {
            identities.values.filter {
                it.subjectDn?.equals(subjectDn, ignoreCase = true) == true
            }
        }

    override suspend fun findIdentitiesBySkiHex(skiHex: String): Flow<ServiceIdentity> =
        snapshotFlow {
            identities.values.filter {
                it.subjectKeyIdentifierHex?.equals(skiHex, ignoreCase = true) == true
            }
        }

    override suspend fun findIdentitiesByIssuerAndSerial(issuerDn: String, serialNumber: String): Flow<ServiceIdentity> =
        snapshotFlow {
            identities.values.filter {
                it.issuerDn?.equals(issuerDn, ignoreCase = true) == true &&
                        it.serialNumber?.equals(serialNumber, ignoreCase = true) == true
            }
        }

    // ---------------------------------------------------------------------------
    // Entity queries
    // ---------------------------------------------------------------------------

    override suspend fun getEntity(entityId: String): TrustedEntity? = mutex.withLock {
        entities[entityId]
    }

    override suspend fun listEntities(filter: EntityFilter): Flow<TrustedEntity> =
        snapshotFlow {
            entities.values.filter { entity ->
                (filter.sourceFamily == null || sources[entity.sourceId]?.sourceFamily == filter.sourceFamily) &&
                        (filter.entityType == null || entity.entityType == filter.entityType) &&
                        (filter.country == null || entity.country?.equals(filter.country, ignoreCase = true) == true) &&
                        (!filter.onlyCurrentlyTrusted || isCurrentlyTrusted(entity.entityId))
            }
        }

    private fun isCurrentlyTrusted(entityId: String): Boolean =
        services.values.any { svc ->
            svc.entityId == entityId && svc.status in setOf(
                TrustStatus.GRANTED, TrustStatus.RECOGNIZED, TrustStatus.ACCREDITED, TrustStatus.SUPERVISED
            )
        }

    // ---------------------------------------------------------------------------
    // Service queries
    // ---------------------------------------------------------------------------

    override suspend fun getService(serviceId: String): TrustedService? = mutex.withLock {
        services[serviceId]
    }

    override suspend fun listServicesForEntity(entityId: String): Flow<TrustedService> =
        snapshotFlow { services.values.filter { it.entityId == entityId } }

    // ---------------------------------------------------------------------------
    // Count helpers (for health reporting)
    // ---------------------------------------------------------------------------

    override suspend fun countEntities(sourceId: String): Int = mutex.withLock {
        entities.values.count { it.sourceId == sourceId }
    }

    override suspend fun countServices(sourceId: String): Int = mutex.withLock {
        services.values.count { it.sourceId == sourceId }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Takes a snapshot of data under the mutex and emits it as a Flow.
     * The lock is held only for the duration of the snapshot, not during consumption.
     */
    private fun <T> snapshotFlow(snapshot: () -> List<T>): Flow<T> =
        flow {
            val items = mutex.withLock { snapshot() }
            items.forEach { emit(it) }
        }
}
