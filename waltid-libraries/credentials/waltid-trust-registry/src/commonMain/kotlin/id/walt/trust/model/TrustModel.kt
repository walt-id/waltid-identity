package id.walt.trust.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

// ---------------------------------------------------------------------------
// Source families
// ---------------------------------------------------------------------------

@Serializable
enum class SourceFamily { TSL, LOTE, PILOT }

// ---------------------------------------------------------------------------
// Authenticity / freshness state
// ---------------------------------------------------------------------------

@Serializable
enum class AuthenticityState { VALIDATED, FAILED, SKIPPED_DEMO, UNKNOWN }

@Serializable
enum class FreshnessState { FRESH, STALE, EXPIRED, UNKNOWN }

// ---------------------------------------------------------------------------
// Trust source
// ---------------------------------------------------------------------------

@Serializable
data class TrustSource(
    val sourceId: String,
    val sourceFamily: SourceFamily,
    val displayName: String,
    val sourceUrl: String? = null,
    val territory: String? = null,
    val issueDate: Instant? = null,
    val nextUpdate: Instant? = null,
    val sequenceNumber: String? = null,
    val authenticityState: AuthenticityState = AuthenticityState.UNKNOWN,
    val freshnessState: FreshnessState = FreshnessState.UNKNOWN,
    val rawArtifactRef: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

// ---------------------------------------------------------------------------
// Trusted entity
// ---------------------------------------------------------------------------

@Serializable
enum class TrustedEntityType {
    TRUST_SERVICE_PROVIDER,
    PID_PROVIDER,
    WALLET_PROVIDER,
    ATTESTATION_PROVIDER,
    ACCESS_CERTIFICATE_PROVIDER,
    RELYING_PARTY_PROVIDER,
    OTHER
}

@Serializable
data class TrustedEntity(
    val entityId: String,
    val sourceId: String,
    val entityType: TrustedEntityType,
    val legalName: String,
    val tradeName: String? = null,
    val registrationNumber: String? = null,
    val country: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

// ---------------------------------------------------------------------------
// Trusted service / role
// ---------------------------------------------------------------------------

@Serializable
enum class TrustStatus {
    GRANTED, RECOGNIZED, ACCREDITED, SUPERVISED,
    DEPRECATED, SUSPENDED, REVOKED, WITHDRAWN, EXPIRED, UNKNOWN
}

@Serializable
data class TrustedService(
    val serviceId: String,
    val sourceId: String,
    val entityId: String,
    val serviceType: String,
    val status: TrustStatus,
    val statusStart: Instant? = null,
    val metadata: Map<String, String> = emptyMap()
)

// ---------------------------------------------------------------------------
// Service identity (the cryptographic handles used for matching)
// ---------------------------------------------------------------------------

@Serializable
data class ServiceIdentity(
    val identityId: String,
    val sourceId: String,
    val entityId: String,
    val serviceId: String? = null,
    val certificateSha256Hex: String? = null,
    val subjectDn: String? = null,
    val issuerDn: String? = null,
    val serialNumber: String? = null,
    val subjectKeyIdentifierHex: String? = null,
    val publicKeyJwkThumbprint: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

// ---------------------------------------------------------------------------
// Trust decision
// ---------------------------------------------------------------------------

@Serializable
enum class TrustDecisionCode {
    TRUSTED, NOT_TRUSTED, UNKNOWN,
    STALE_SOURCE, MULTIPLE_MATCHES, UNSUPPORTED_SOURCE, PROCESSING_ERROR
}

@Serializable
data class TrustEvidence(
    val type: String,
    val value: String,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class TrustDecision(
    val decision: TrustDecisionCode,
    val sourceFreshness: FreshnessState = FreshnessState.UNKNOWN,
    val authenticity: AuthenticityState = AuthenticityState.UNKNOWN,
    val matchedSource: TrustSource? = null,
    val matchedEntity: TrustedEntity? = null,
    val matchedService: TrustedService? = null,
    val evidence: List<TrustEvidence> = emptyList(),
    val warnings: List<String> = emptyList()
)

// ---------------------------------------------------------------------------
// Query filters
// ---------------------------------------------------------------------------

@Serializable
data class EntityFilter(
    val sourceFamily: SourceFamily? = null,
    val entityType: TrustedEntityType? = null,
    val country: String? = null,
    val onlyCurrentlyTrusted: Boolean = false
)

// ---------------------------------------------------------------------------
// Source health
// ---------------------------------------------------------------------------

@Serializable
data class TrustSourceHealth(
    val sourceId: String,
    val displayName: String,
    val sourceFamily: SourceFamily,
    val freshnessState: FreshnessState,
    val authenticityState: AuthenticityState,
    val nextUpdate: Instant? = null,
    val entityCount: Int = 0,
    val serviceCount: Int = 0
)

// ---------------------------------------------------------------------------
// Refresh result
// ---------------------------------------------------------------------------

@Serializable
data class RefreshResult(
    val sourceId: String,
    val success: Boolean,
    val entitiesLoaded: Int = 0,
    val servicesLoaded: Int = 0,
    val identitiesLoaded: Int = 0,
    val error: String? = null
)
