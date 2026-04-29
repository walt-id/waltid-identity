package id.walt.trust.store

import id.walt.trust.model.*

/**
 * Abstraction over the trust data store.
 * MVP implementation: [InMemoryTrustStore].
 * Enterprise extension: implement in waltid-identity-enterprise with DB backing.
 */
interface TrustStore {
    suspend fun upsertSource(source: TrustSource)
    suspend fun upsertEntities(entities: List<TrustedEntity>)
    suspend fun upsertServices(services: List<TrustedService>)
    suspend fun upsertIdentities(identities: List<ServiceIdentity>)

    /**
     * Atomically replace all data for a source.
     * Removes existing entities/services/identities for the sourceId, then inserts new ones.
     * This prevents partial state if individual upserts would fail.
     */
    suspend fun replaceSourceData(
        source: TrustSource,
        entities: List<TrustedEntity>,
        services: List<TrustedService>,
        identities: List<ServiceIdentity>
    )

    suspend fun getSource(sourceId: String): TrustSource?
    suspend fun listSources(): List<TrustSource>
    suspend fun updateSourceFreshness(sourceId: String, freshnessState: FreshnessState)
    suspend fun updateSourceAuthenticity(sourceId: String, authenticityState: AuthenticityState)

    suspend fun findIdentitiesByCertificateSha256(sha256Hex: String): List<ServiceIdentity>
    suspend fun findIdentitiesBySubjectDn(subjectDn: String): List<ServiceIdentity>
    suspend fun findIdentitiesBySkiHex(skiHex: String): List<ServiceIdentity>
    suspend fun findIdentitiesByIssuerAndSerial(issuerDn: String, serialNumber: String): List<ServiceIdentity>

    suspend fun getEntity(entityId: String): TrustedEntity?
    suspend fun listEntities(filter: EntityFilter): List<TrustedEntity>

    suspend fun getService(serviceId: String): TrustedService?
    suspend fun listServicesForEntity(entityId: String): List<TrustedService>

    suspend fun countEntities(sourceId: String): Int
    suspend fun countServices(sourceId: String): Int
}
