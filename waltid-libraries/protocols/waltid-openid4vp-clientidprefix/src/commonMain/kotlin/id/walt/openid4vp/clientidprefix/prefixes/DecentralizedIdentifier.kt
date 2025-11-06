package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.did.dids.DidService
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext
import id.walt.verifier.openid.models.authorization.ClientMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonPrimitive

/**
 * Handles `decentralized_identifier` prefix per OpenID4VP 1.0, Section 5.9.3.
 */
@Serializable
data class DecentralizedIdentifier(val did: String, override val rawValue: String) : ClientId {

    companion object {
        // TODO: Is DID regex fully correct?
        private val didRegex = "^did:[a-z0-9]+:.+".toRegex()
    }

    init {
        require(didRegex.matches(did)) { "Invalid DID format." }
    }

    suspend fun authenticateDecentralizedIdentifier(
        clientId: DecentralizedIdentifier,
        context: RequestContext
    ): ClientValidationResult {
        val jws = context.requestObjectJws
            ?: return ClientValidationResult.Failure(ClientIdError.MissingRequestObject)

        return runCatching {
            val kid = jws.decodeJws().header["kid"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("Missing 'kid' header in JWS for key selection.")

            // 1. Resolve all keys from the DID document.
            val keys = DidService.resolveToKeys(clientId.did).getOrThrow()

            // 2. Find the specific key used for signing.
            val verificationKey = keys.find { it.getKeyId() == kid }
                ?: throw IllegalArgumentException("Key ID '$kid' from JWS not found in DID document.")

            // 3. Verify the JWS signature with that key.
            verificationKey.verifyJws(jws).getOrThrow()

            val metadataJson = context.clientMetadataJson
                ?: throw IllegalStateException("client_metadata parameter is required.")

            ClientMetadata.fromJson(metadataJson).getOrThrow()
        }.fold(
            onSuccess = { ClientValidationResult.Success(it) },
            onFailure = { ClientValidationResult.Failure(ClientIdError.DidResolutionFailed(it.message!!)) }
        )
    }
}
