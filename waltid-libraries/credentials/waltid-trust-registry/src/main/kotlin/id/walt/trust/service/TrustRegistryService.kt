package id.walt.trust.service

import id.walt.trust.model.*
import kotlin.time.Instant

/**
 * Main service interface for trust registry operations.
 * See: waltid-architecture/enterprise/trust-lists/05-api-design.md
 */
interface TrustRegistryService {
    
    /**
     * Resolve trust status for a certificate (by PEM or DER base64).
     */
    suspend fun resolveByCertificate(
        certificatePemOrDer: String,
        instant: Instant,
        expectedEntityType: TrustedEntityType? = null,
        expectedServiceType: String? = null
    ): TrustDecision

    /**
     * Resolve trust status by certificate SHA-256 fingerprint (hex string).
     */
    suspend fun resolveByCertificateSha256(
        sha256Hex: String,
        instant: Instant,
        expectedEntityType: TrustedEntityType? = null,
        expectedServiceType: String? = null
    ): TrustDecision

    /**
     * Resolve trust status for a public key (JWK format).
     */
    suspend fun resolveByPublicKey(
        jwk: String,
        instant: Instant,
        expectedEntityType: TrustedEntityType? = null,
        expectedServiceType: String? = null
    ): TrustDecision

    /**
     * Resolve trust status by provider/entity ID.
     */
    suspend fun resolveByProviderId(
        providerId: String,
        instant: Instant,
        expectedEntityType: TrustedEntityType? = null
    ): TrustDecision

    /**
     * List trusted entities with optional filtering.
     */
    suspend fun listTrustedEntities(filter: EntityFilter = EntityFilter()): List<TrustedEntity>

    /**
     * List all registered trust sources.
     */
    suspend fun listSources(): List<TrustSource>

    /**
     * Get health information for all sources.
     */
    suspend fun getSourceHealth(): List<TrustSourceHealth>

    /**
     * Refresh a specific source by ID.
     */
    suspend fun refreshSource(sourceId: String): RefreshResult

    /**
     * Load a source from raw content (for bootstrapping / demo).
     */
    suspend fun loadSourceFromContent(
        sourceId: String,
        content: String,
        sourceUrl: String? = null
    ): RefreshResult
}
