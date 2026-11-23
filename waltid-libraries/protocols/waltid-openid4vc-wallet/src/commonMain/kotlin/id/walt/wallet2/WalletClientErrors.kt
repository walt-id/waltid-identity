package id.walt.wallet2

import id.walt.errors.HttpStatusError

/**
 * A wallet failure the caller can act on, carrying the HTTP status a service answers it with ([HttpStatusError]).
 * Its message is wallet-written for the caller, who owns the wallet, so the service's error handling returns it.
 *
 * It stays an [IllegalStateException], which these failures were before they carried a status, so existing
 * callers that catch that type keep working.
 */
open class WalletClientException(
    override val status: Int,
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause), HttpStatusError

/** The request cannot be served as given: a missing or conflicting parameter, an unknown key, an unsupported offer. */
open class WalletRequestException(message: String, cause: Throwable? = null) : WalletClientException(400, message, cause)

/**
 * The wallet's current state does not allow the operation: an issuance session in another phase, a credential or
 * key that changed or disappeared since it was reviewed, or a concurrent operation still running. Retrying after
 * refreshing that state can succeed.
 */
open class WalletStateConflictException(message: String, cause: Throwable? = null) : WalletClientException(409, message, cause)
