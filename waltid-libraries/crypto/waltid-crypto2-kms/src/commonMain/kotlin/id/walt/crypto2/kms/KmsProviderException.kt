package id.walt.crypto2.kms

/**
 * A key management service failed or returned an unusable answer.
 *
 * @property httpStatus the HTTP status of the provider response that failed, or `null` when the failure was not an
 * HTTP error status
 */
class KmsProviderException internal constructor(
    message: String,
    cause: Throwable?,
    val httpStatus: Int?,
) : Exception(message, cause) {
    constructor(message: String, cause: Throwable? = null) : this(message, cause, null)
}
