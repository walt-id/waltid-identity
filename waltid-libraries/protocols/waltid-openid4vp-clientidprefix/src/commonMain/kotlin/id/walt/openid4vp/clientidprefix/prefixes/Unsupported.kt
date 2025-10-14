package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext

/**
 * Handles an unsupported prefix.
 */
class Unsupported(override val context: RequestContext, private val prefix: String) : ClientId {
    override suspend fun validate(): ClientValidationResult {
        return ClientValidationResult.Failure(ClientIdError.UnsupportedPrefix(prefix))
    }
}
