package id.walt.trust.store

import id.walt.trust.model.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe in-memory implementation of [TrustStore].
 * Suitable for MVP / demo use. Not persistent across restarts.
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

    override suspend fun listSources(): List<TrustSource> = mutex.withLock {
        sources.values.toList()
    }

    // ---------------------------------------------------------------------------
    // Identity lookups
    // ---------------------------------------------------------------------------

    override suspend fun findIdentitiesByCertificateSha256(sha256Hex: String): List<ServiceIdentity> =
        mutex.withLock {
            identities.values.filter {
                it.certificateSha256Hex?.equals(sha256Hex, ignoreCase = true) == true
            }
        }

    override suspend fun findIdentitiesBySubjectDn(subjectDn: String): List<ServiceIdentity> =
        mutex.withLock {
            identities.values.filter {
                it.subjectDn?.equals(subjectDn, ignoreCase = true) == true
            }
        }

    override suspend fun findIdentitiesBySkiHex(skiHex: String): List<ServiceIdentity> =
        mutex.withLock {
            identities.values.filter {
                it.subjectKeyIdentifierHex?.equals(skiHex, ignoreCase = true) == true
            }
        }

    override suspend fun findIdentitiesByIssuerAndSerial(issuerDn: String, serialNumber: String): List<ServiceIdentity> =
        mutex.withLock {
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

    override suspend fun listEntities(filter: EntityFilter): List<TrustedEntity> = mutex.withLock {
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

    override suspend fun listServicesForEntity(entityId: String): List<TrustedService> = mutex.withLock {
        services.values.filter { it.entityId == entityId }
    }

    // ---------------------------------------------------------------------------
    // Count helpers (for health reporting)
    // ---------------------------------------------------------------------------

    override suspend fun countEntities(sourceId: String): Int = mutex.withLock {
        entities.values.count { it.sourceId == sourceId }
    }

    override suspend fun countServices(sourceId: String): Int = mutex.withLock {
        services.values.count { it.sourceId == sourceId }
    }
}
