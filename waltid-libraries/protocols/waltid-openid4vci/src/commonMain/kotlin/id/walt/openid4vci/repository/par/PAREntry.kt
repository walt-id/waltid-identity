package id.walt.openid4vci.repository.par

import kotlin.time.Instant

/**
 * PAR repository entry representing a stored pushed authorization request.
 *
 * This is the domain model for PAR storage, independent of the underlying persistence mechanism.
 */
data class PAREntry(
    /**
     * Unique identifier for this PAR (the reference value in request_uri)
     */
    val requestId: String,

    /**
     * Stored authorization request parameters, preserving multi-valued form data.
     */
    val requestParameters: Map<String, List<String>>,

    /**
     * When this PAR entry was created
     */
    val createdAt: Instant,

    /**
     * When this PAR entry expires (RFC 9126: short-lived, recommended 90s)
     */
    val expiresAt: Instant,

    /**
     * Whether this PAR has been consumed (single-use)
     */
    val consumed: Boolean = false,

    /**
     * Optional client attestation or authentication metadata
     */
    val clientMetadata: Map<String, String> = emptyMap(),
) {
    val clientId: String = requestParameters["client_id"].orEmpty().singleOrNull()?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("PAR entry must contain exactly one non-blank client_id")

    init {
        require(requestId.isNotBlank()) { "requestId must not be blank" }
        require(expiresAt > createdAt) { "expiresAt must be after createdAt" }
        require(requestParameters["request_uri"].orEmpty().none { it.isNotBlank() }) {
            "PAR entry must not contain request_uri"
        }
    }

    /**
     * Check if this PAR entry is still valid (not expired, not consumed)
     */
    fun isValid(now: Instant): Boolean =
        !consumed && now < expiresAt

    /**
     * Mark this PAR as consumed
     */
    fun markConsumed(): PAREntry =
        copy(consumed = true)
}