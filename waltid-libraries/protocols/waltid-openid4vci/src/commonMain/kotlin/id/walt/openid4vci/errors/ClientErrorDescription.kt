package id.walt.openid4vci.errors

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

/**
 * This exception's message, when it describes the client's own request and is therefore safe to return as an
 * OAuth `error_description` or to record on an issuance session or event; otherwise null.
 *
 * An [IllegalArgumentException] (which includes kotlinx.serialization's `SerializationException`) is how this
 * library and its parsers reject a request, token or proof, and a [CredentialProtocolException] carries an
 * error the issuer wrote for the wallet. Anything else is an internal failure - a session store, KMS or network
 * error - whose message can name hosts, ports or driver state.
 */
fun Throwable.clientVisibleDescription(): String? = when (this) {
    is IllegalArgumentException, is CredentialProtocolException -> message
    else -> null
}

/**
 * The `error_description` for a failure a catch block answers itself: the exception's own message when it
 * describes the client's request (see [clientVisibleDescription]), otherwise [fallback] with an errorId. The
 * exception is then logged under that errorId, so support can find it from what the client was shown.
 *
 * A catch block that rethrows should use [clientVisibleDescription] instead: the application's error handling
 * already logs a rethrown exception and assigns it an errorId.
 */
fun clientErrorDescription(cause: Throwable, fallback: String, logContext: String = fallback): String =
    cause.clientVisibleDescription() ?: run {
        val errorId = Uuid.random().toString()
        log.error(cause) { "$logContext (errorId=$errorId)" }
        "$fallback (errorId=$errorId)"
    }
