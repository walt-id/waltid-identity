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
     * Resolve a presented leaf-first certificate chain against certificates owned by
     * the registry. The presented chain must omit the trust anchor when required by
     * the applicable credential profile.
     */
    suspend fun resolveCertificateChain(
        certificateChainPemOrDer: List<String>,
        instant: Instant,
        expectedEntityType: TrustedEntityType? = null,
        expectedServiceType: String? = null
    ): TrustDecision

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
     * Load and admit a source using an explicit verification and acceptance policy.
     * The default [SourceLoadOptions] policy requires an authenticated signer.
     */
    suspend fun loadSourceFromContent(
        sourceId: String,
        content: String,
        sourceUrl: String? = null,
        options: SourceLoadOptions
    ): RefreshResult

    /** Load a remote source using an explicit verification and acceptance policy. */
    suspend fun loadSourceFromUrl(
        sourceId: String,
        url: String,
        options: SourceLoadOptions
    ): RefreshResult

    /**
     * Load a source from raw content through the deprecated compatibility API.
     *
     * @param sourceId Unique identifier for this trust source
     * @param content Raw trust list content (TSL XML, LoTE JSON/XML)
     * @param sourceUrl Optional URL to store for future refresh calls
     * @param validateSignature Whether to validate XMLDSig signatures (for TSL sources)
     * @param trustedSignerCertificates PEM or Base64-DER certificates trusted to sign compact-JWS LoTE sources
     */
    @Deprecated("Use the SourceLoadOptions overload with an explicit acceptance policy")
    suspend fun loadSourceFromContent(
        sourceId: String,
        content: String,
        sourceUrl: String? = null,
        validateSignature: Boolean = true,
        trustedSignerCertificates: List<String> = emptyList()
    ): RefreshResult

    /**
     * Load a source directly from a URL.
     * Fetches the content via HTTP and parses it.
     *
     * @param sourceId Unique identifier for this trust source
     * @param url URL to fetch the trust list from
     * @param validateSignature Whether to validate XMLDSig signatures (for TSL sources)
     * @param trustedSignerCertificates PEM or Base64-DER certificates trusted to sign compact-JWS LoTE sources
     */
    @Deprecated("Use the SourceLoadOptions overload with an explicit acceptance policy")
    suspend fun loadSourceFromUrl(
        sourceId: String,
        url: String,
        validateSignature: Boolean = true,
        trustedSignerCertificates: List<String> = emptyList()
    ): RefreshResult
}
