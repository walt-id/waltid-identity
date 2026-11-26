package id.walt.wallet2.mobile.proximity

import kotlin.uuid.Uuid

/**
 * Credential and disclosure choice for exactly one reviewed document request.
 *
 * @property requestIndex Reviewed document request being answered.
 * @property credentialId Reviewed credential selected for the response.
 * @property disclosedElements Non-empty subset of reviewed elements approved for disclosure.
 */
public data class ProximityDocumentSubmission(
    public val requestIndex: Int,
    public val credentialId: String,
    public val disclosedElements: Set<ProximityElementReference>,
) {
    init {
        require(requestIndex >= 0 && credentialId.isNotBlank())
        require(disclosedElements.isNotEmpty()) { "A document submission must disclose at least one reviewed element" }
    }
}

/**
 * Complete holder choice for the current review; it is rebound and revalidated before response generation.
 *
 * @property documents One credential and disclosure choice per answered document request.
 * @property continueAfterResponse Whether to remain connected for another request after a successful response.
 */
public data class ProximitySubmission(
    public val documents: List<ProximityDocumentSubmission>,
    public val continueAfterResponse: Boolean = false,
) {
    init {
        require(documents.isNotEmpty())
        require(documents.distinctBy { it.requestIndex }.size == documents.size)
    }
}

/** Opaque identity issued by the wallet for one consent review. */
public data class ProximityReviewId(
    /** Stable representation used by the Swift bridge and host state restoration. */
    public val value: String,
) {
    init { Uuid.parse(value) }
}
