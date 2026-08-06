@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.did.dids.DidService
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext
import kotlinx.serialization.Serializable
import kotlinx.coroutines.CancellationException
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

        return try {
            val decoded = CompactJws.decodeUnverified(jws)
            val kid = decoded.protectedHeader["kid"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("Missing 'kid' header in JWS for key selection.")

            if (decoded.algorithm == JwsAlgorithm.ES256K) {
                val verificationKey = DidService.resolveToKeys(clientId.did).getOrThrow()
                    .find { it.getKeyId() == kid }
                    ?: throw IllegalArgumentException("Key ID '$kid' from JWS not found in DID document.")
                verificationKey.verifyJws(jws).getOrThrow()
            } else {
                val verificationKey = DidService.resolveToCrypto2Keys(clientId.did).getOrThrow()
                    .find { it.id.value == kid }
                    ?: throw IllegalArgumentException("Key ID '$kid' from JWS not found in DID document.")
                ClientIdCrypto2.verify(jws, verificationKey)
            }

            val metadataJson = context.clientMetadata
                ?: throw IllegalStateException("client_metadata parameter is required.")
            ClientValidationResult.Success(metadataJson)
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            ClientValidationResult.Failure(ClientIdError.DidResolutionFailed(cause.message ?: "DID verification failed"))
        }
    }
}
