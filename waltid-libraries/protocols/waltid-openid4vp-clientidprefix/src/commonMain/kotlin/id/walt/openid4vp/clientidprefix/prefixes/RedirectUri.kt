package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientMetadata
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext

/**
 * Handles `redirect_uri` prefix per OpenID4VP 1.0, Section 5.9.3.
 */
class RedirectUri(override val context: RequestContext) : ClientId {
    override suspend fun validate(): ClientValidationResult {
        // Requests using this prefix cannot be signed.
        if (context.requestObjectJws != null) {
            return ClientValidationResult.Failure(ClientIdError.InvalidSignature)
        }
        // All Verifier metadata parameters MUST be passed using the client_metadata parameter.
        return context.clientMetadataJson?.let {
            ClientValidationResult.Success(ClientMetadata(it))
        } ?: ClientValidationResult.Failure(ClientIdError.MissingClientMetadata)
    }
}
