package id.walt.trust.service

import id.walt.trust.model.*
import kotlinx.coroutines.flow.Flow
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
    suspend fun listTrustedEntities(filter: EntityFilter = EntityFilter()): Flow<TrustedEntity>

    /**
     * List all registered trust sources.
     */
    suspend fun listSources(): Flow<TrustSource>

    /**
     * Get health information for all sources.
     */
    suspend fun getSourceHealth(): Flow<TrustSourceHealth>

    /**
     * Refresh a specific source by ID.
     */
    suspend fun refreshSource(sourceId: String): RefreshResult

    /**
     * Load a source from raw content (for bootstrapping / demo).
     *
     * @param sourceId Unique identifier for this trust source
     * @param content Raw trust list content (TSL XML, LoTE JSON/XML)
     * @param sourceUrl Optional URL to store for future refresh calls
     * @param validateSignature Whether to validate XMLDSig signatures (for TSL sources)
     */
    suspend fun loadSourceFromContent(
        sourceId: String,
        content: String,
        sourceUrl: String? = null,
        validateSignature: Boolean = true
    ): RefreshResult

    /**
     * Load a source directly from a URL.
     * Fetches the content via HTTP and parses it.
     *
     * @param sourceId Unique identifier for this trust source
     * @param url URL to fetch the trust list from
     * @param validateSignature Whether to validate XMLDSig signatures (for TSL sources)
     */
    suspend fun loadSourceFromUrl(
        sourceId: String,
        url: String,
        validateSignature: Boolean = true
    ): RefreshResult
}
