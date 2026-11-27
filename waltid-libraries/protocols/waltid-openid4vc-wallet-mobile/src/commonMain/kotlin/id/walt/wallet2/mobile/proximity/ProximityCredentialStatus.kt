package id.walt.wallet2.mobile.proximity

import kotlin.time.Instant

/** Current status of a credential at the explicit application status boundary. */
public enum class ProximityCredentialStatus {
    Valid,
    Revoked,
    Indeterminate,
}

/**
 * Metadata-only status input; raw credential values are not handed to network providers.
 *
 * @property credentialId Stable wallet-local credential identifier.
 * @property docType Credential mdoc document type.
 * @property issuer Display-safe issuer identifier when available.
 * @property validFrom Start of the locally verified MSO validity interval.
 * @property validUntil End of the locally verified MSO validity interval.
 */
public data class ProximityCredentialStatusInput(
    public val credentialId: String,
    public val docType: String,
    public val issuer: String?,
    public val validFrom: Instant,
    public val validUntil: Instant,
) {
    init {
        require(credentialId.isNotBlank() && docType.isNotBlank())
        require(validUntil >= validFrom)
    }
}

/** Explicit, optionally network-backed status boundary. The SDK itself performs no hidden lookup. */
public fun interface ProximityCredentialStatusEvaluator {
    /** Evaluates the current status of [credential] without receiving raw credential values. */
    public suspend fun evaluate(
        credential: ProximityCredentialStatusInput,
    ): ProximityCredentialStatus
}

/** Default status policy relies on the locally verified MSO validity interval only. */
public object UnconfiguredProximityCredentialStatusEvaluator :
    ProximityCredentialStatusEvaluator {
    /** Accepts the credential after the SDK has verified its local MSO validity interval. */
    override suspend fun evaluate(
        credential: ProximityCredentialStatusInput,
    ): ProximityCredentialStatus = ProximityCredentialStatus.Valid
}
