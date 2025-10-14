package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext

/**
 * Handles a pre-registered client (no prefix) per OpenID4VP 1.0, Section 5.9.2.
 */
class PreRegistered(override val context: RequestContext) : ClientId {
    override suspend fun validate(): ClientValidationResult {
        // TODO: Implement logic to look up the client in a pre-configured database.
        // val metadataJson = database.findClient(context.clientId)
        // return if (metadataJson != null) {
        //     ClientValidationResult.Success(ClientMetadata(metadataJson))
        // } else {
        //     ClientValidationResult.Failure(ClientIdError.PreRegisteredClientNotFound(context.clientId))
        // }
        return ClientValidationResult.Failure(ClientIdError.PreRegisteredClientNotFound(context.clientId))
    }
}
