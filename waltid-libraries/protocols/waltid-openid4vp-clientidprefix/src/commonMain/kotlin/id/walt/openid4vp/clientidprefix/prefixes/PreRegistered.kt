package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.verifier.openid.models.authorization.ClientMetadata
import kotlinx.serialization.Serializable

/**
 * Handles a pre-registered client (no prefix) per OpenID4VP 1.0, Section 5.9.2.
 */
@Serializable
data class PreRegistered(override val rawValue: String) : ClientId {

    suspend fun authenticatePreRegistered(
        clientId: PreRegistered,
        preRegisteredMetadataProvider: suspend (String) -> String?
    ): ClientValidationResult {
        // library expects the calling layer to provide the metadata
        val metadataJson = preRegisteredMetadataProvider(clientId.rawValue)
            ?: return ClientValidationResult.Failure(ClientIdError.PreRegisteredClientNotFound(clientId.rawValue))

        return ClientMetadata.fromJson(metadataJson).fold(
            onSuccess = { ClientValidationResult.Success(it) },
            onFailure = { ClientValidationResult.Failure(ClientIdError.InvalidMetadata("Stored metadata is malformed: ${it.message}")) }
        )
    }

}
