package id.walt.trust.store

import id.walt.trust.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction over the trust data store.
 * [InMemoryTrustStore] provides the default non-persistent implementation.
 * Persistent implementations can provide database-backed storage.
 *
 * Query methods that return multiple items produce a [Flow] so callers can stream
 * results without first materialising the entire result set into a List.
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
    suspend fun listSources(): Flow<TrustSource>
    suspend fun updateSourceFreshness(sourceId: String, freshnessState: FreshnessState)
    suspend fun updateSourceAssurance(sourceId: String, assurance: SourceAssurance)

    suspend fun findIdentitiesByCertificateSha256(sha256Hex: String): Flow<ServiceIdentity>
    suspend fun listCertificateIdentities(): Flow<ServiceIdentity>

    /**
     * The certificate-bearing identities whose [ServiceIdentity.canonicalSubjectSha256Hex] is one of [hashes],
     * plus those that have none (stored before it existed, or with a certificate that does not parse).
     *
     * Chain resolution reads its candidate anchors through this instead of [listCertificateIdentities] and still
     * compares each parsed subject, so a store may return more than asked for but never less. The default
     * returns every certificate identity, which is correct for any store, just not narrowed.
     */
    suspend fun findCertificateIdentitiesByCanonicalSubject(hashes: Set<String>): Flow<ServiceIdentity> =
        listCertificateIdentities()
    suspend fun findIdentitiesBySubjectDn(subjectDn: String): Flow<ServiceIdentity>
    suspend fun findIdentitiesBySkiHex(skiHex: String): Flow<ServiceIdentity>
    suspend fun findIdentitiesByIssuerAndSerial(issuerDn: String, serialNumber: String): Flow<ServiceIdentity>
    suspend fun findIdentitiesByJwkThumbprint(sha256Thumbprint: String): Flow<ServiceIdentity>

    suspend fun getEntity(entityId: String): TrustedEntity?
    suspend fun listEntities(filter: EntityFilter): Flow<TrustedEntity>

    suspend fun getService(serviceId: String): TrustedService?
    suspend fun listServicesForEntity(entityId: String): Flow<TrustedService>

    suspend fun countEntities(sourceId: String): Int
    suspend fun countServices(sourceId: String): Int
}
