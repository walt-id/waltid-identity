package id.walt.openid4vci.errors

import id.walt.errors.ClientFacingError
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

/**
 * This exception's message, when it was written for the client and is therefore safe to return as an OAuth
 * `error_description` or to record on an issuance session or event; otherwise null.
 *
 * Only explicit markers qualify: a [ClientFacingError] (such as [id.walt.errors.ClientRequestException] or
 * `CredentialProofValidationException`), thrown by validators for checks on what the client sent, and a
 * [CredentialProtocolException], which carries an error the issuer wrote for the wallet. Any other exception, including a plain [IllegalArgumentException] or a
 * kotlinx.serialization `SerializationException`, may come from issuer configuration, key material, stored data
 * or a session store, KMS or network client, and its message can name hosts, ports or resources.
 */
fun Throwable.clientVisibleDescription(): String? = when (this) {
    is ClientFacingError, is CredentialProtocolException -> message
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
