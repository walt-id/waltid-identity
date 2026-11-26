package id.walt.errors

/**
 * Marks an exception whose message was written for the client, so it may be returned to the client (an HTTP body,
 * an OAuth `error_description`) and recorded where clients can read it (session records, events, webhooks).
 *
 * The exception type alone does not establish this: `require` checks and kotlinx.serialization also fail with an
 * [IllegalArgumentException] on configuration, key material and stored data, whose messages can name hosts or
 * resources. Code that turns exceptions into client-visible text keeps the message only for errors marked this
 * way, and otherwise reports a generic description with an errorId under which the cause is logged.
 *
 * Throw [ClientRequestException] or [ClientStateConflictException], or implement this on a domain exception whose
 * message is fixed, walt-written text about the caller's own request or resources.
 */
interface ClientFacingError

/**
 * The request cannot be served as given: a missing, invalid or conflicting parameter, a proof or token that does
 * not verify, a resource the caller named that does not exist in its scope. Answered with 400 and its message.
 */
open class ClientRequestException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause), ClientFacingError, HttpStatusError {
    override val status: Int get() = 400
}

/**
 * The current state does not allow the operation: a session in another phase or in use by another request, a
 * resource that changed or disappeared since it was reviewed, a misconfiguration the caller owns. Retrying after
 * refreshing that state can succeed. Answered with 409 and its message.
 */
open class ClientStateConflictException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause), ClientFacingError, HttpStatusError {
    override val status: Int get() = 409
}
