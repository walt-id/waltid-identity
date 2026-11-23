package id.walt.openid4vci.errors

/**
 * A rejection of the client's own request, token or proof, whose message was written to be returned to the client
 * as an OAuth `error_description` and recorded on issuance events.
 *
 * A plain [IllegalArgumentException] is not enough for that: `require` checks and kotlinx.serialization also fail
 * this way on issuer configuration, key material and stored data, whose messages can name hosts or resources.
 * Validators throw this type only for checks on what the client sent.
 */
open class ClientInputException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/** Like `require`, for a check on client input: throws [ClientInputException]. */
internal inline fun ensureClientInput(condition: Boolean, message: () -> String) {
    if (!condition) throw ClientInputException(message())
}
