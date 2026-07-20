package id.walt.trust.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

// ---------------------------------------------------------------------------
// Source families
// ---------------------------------------------------------------------------

@Serializable
enum class SourceFamily { TSL, LOTE, PILOT }

// ---------------------------------------------------------------------------
// Source assurance / freshness state
// ---------------------------------------------------------------------------

@Serializable
enum class AuthenticityState {
    /** Signature is valid and the signer chains to an independently trusted certificate. */
    AUTHENTICATED,

    /** Signature integrity is valid, but signer authorization was not established. */
    INTEGRITY_VERIFIED,

    /** Source is unsigned or signature verification was explicitly disabled. */
    UNVERIFIED,

    /** Signature, signer trust, or source verification failed. */
    FAILED,

    UNKNOWN
}

@Serializable
enum class SignatureStatus { NOT_PRESENT, NOT_CHECKED, VALID, INVALID, UNSUPPORTED }

@Serializable
enum class SignerTrust { NOT_APPLICABLE, NOT_EVALUATED, TRUSTED, UNTRUSTED }

/**
 * Controls which sources may become active in the registry.
 * Invalid signatures are rejected whenever verification is enabled.
 */
@Serializable
enum class SourceAcceptancePolicy {
    REQUIRE_AUTHENTICATED,
    REQUIRE_VALID_SIGNATURE,
    ALLOW_UNSIGNED,
    ALLOW_UNVERIFIED
}

@Serializable
data class SourceAssurance(
    val signatureStatus: SignatureStatus = SignatureStatus.NOT_CHECKED,
    val signerTrust: SignerTrust = SignerTrust.NOT_EVALUATED,
    val authenticityState: AuthenticityState = AuthenticityState.UNKNOWN,
    val acceptancePolicy: SourceAcceptancePolicy = SourceAcceptancePolicy.REQUIRE_AUTHENTICATED,
    val accepted: Boolean = false,
    val details: String? = null
)

@Serializable
data class SourceLoadOptions(
    val acceptancePolicy: SourceAcceptancePolicy = SourceAcceptancePolicy.REQUIRE_AUTHENTICATED,
    /** PEM or Base64-DER certificates authorized to sign the source. */
    val trustedSignerCertificates: List<String> = emptyList()
)

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
    val assurance: SourceAssurance = SourceAssurance(),
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
    /** Base64-encoded DER certificate retained for PKIX path construction. */
    val certificateDerBase64: String? = null,
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
    val sourceAssurance: SourceAssurance = SourceAssurance(),
    val matchedSource: TrustSource? = null,
    val matchedEntity: TrustedEntity? = null,
    val matchedService: TrustedService? = null,
    val evidence: List<TrustEvidence> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    /** Compatibility projection; prefer [sourceAssurance]. */
    val authenticity: AuthenticityState get() = sourceAssurance.authenticityState
}

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
    val assurance: SourceAssurance,
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
    val error: String? = null,
    val errorCode: SourceLoadErrorCode? = null,
    val assurance: SourceAssurance? = null
)

@Serializable
enum class SourceLoadErrorCode {
    FETCH_FAILED,
    UNKNOWN_FORMAT,
    SOURCE_NOT_ACCEPTED,
    SIGNATURE_VALIDATION_FAILED,
    PARSE_FAILED
}
