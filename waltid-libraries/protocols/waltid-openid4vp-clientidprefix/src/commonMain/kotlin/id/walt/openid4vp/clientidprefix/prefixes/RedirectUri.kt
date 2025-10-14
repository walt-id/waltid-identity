package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext
import id.walt.verifier.openid.models.authorization.ClientMetadata
import io.ktor.http.*
import kotlinx.serialization.Serializable

/**
 * Handles `redirect_uri` prefix per OpenID4VP 1.0, Section 5.9.3.
 */
@Serializable
data class RedirectUri(val uri: Url, override val rawValue: String) : ClientId {
    init {
        require(uri.isAbsolutePath) { "Redirect URI must be an absolute URL." }
    }

    suspend fun authenticateRedirectUri(context: RequestContext): ClientValidationResult {
        if (context.requestObjectJws != null) {
            return ClientValidationResult.Failure(ClientIdError.DoesNotSupportSignature)
        }
        val metadataJson = context.clientMetadataJson
            ?: return ClientValidationResult.Failure(ClientIdError.MissingClientMetadata)

        return ClientMetadata.fromJson(metadataJson).fold(
            onSuccess = { ClientValidationResult.Success(it) },
            onFailure = { ClientValidationResult.Failure(ClientIdError.InvalidMetadata(it.message!!)) }
        )
    }
}
