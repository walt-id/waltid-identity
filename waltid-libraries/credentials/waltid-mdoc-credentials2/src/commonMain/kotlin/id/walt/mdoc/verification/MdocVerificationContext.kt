package id.walt.mdoc.verification

/**
 * A data class to hold the context required for a specific verification transaction.
 * This makes the verifier's public API cleaner.
 *
 * @param expectedNonce The nonce provided by the verifier in the initial request.
 * @param expectedAudience The client_id of the verifier, which is the expected audience.
 * @param responseUri The response_uri from the initial request.
 */
data class MdocVerificationContext(
    val expectedNonce: String,
    val expectedAudience: String?,
    val responseUri: String?,

    val jwkThumbprint: String? = null,
    val isEncrypted: Boolean = false,
    val isDcApi: Boolean = false,
)
