@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.PublicKeyIds
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.did.dids.DidService
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext
import kotlinx.coroutines.CancellationException
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

        return try {
            val decoded = CompactJws.decodeUnverified(jws)
            val kid = decoded.protectedHeader["kid"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("Missing 'kid' header in JWS for key selection.")

            if (decoded.algorithm == JwsAlgorithm.ES256K) {
                val keys = DidService.resolveToKeys(clientId.did).getOrThrow()
                val verificationKey = selectLegacyVerificationKey(clientId.did, kid, keys)
                verificationKey.verifyJws(jws).getOrThrow()
            } else {
                val keys = DidService.resolveToCrypto2Keys(clientId.did).getOrThrow()
                val verificationKey = selectCrypto2VerificationKey(clientId.did, kid, keys)
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

    /**
     * Key selection for OpenID4VP decentralized_identifier:
     * 1. JAR kid is a DID URL for this DID (verification method id) — match fragment to key id/thumbprint
     * 2. Otherwise match RFC 7638 thumbprint / public key id
     * 3. Last resort: single-key DID with a DID-URL kid for this DID (e.g. did:key:<mb>#<mb>)
     */
    private suspend fun selectLegacyVerificationKey(did: String, kid: String, keys: Set<Key>): Key {
        if (kid == did || kid.startsWith("$did#")) {
            val fragment = kid.substringAfter('#', missingDelimiterValue = "")
            if (fragment.isNotEmpty()) {
                keys.find { key ->
                    val publicId = PublicKeyIds.run { key.publicKeyId() }
                    val thumbprint = key.getThumbprint()
                    fragment == publicId || fragment == thumbprint || fragment == key.getKeyId()
                }?.let { return it }
            }
            keys.singleOrNull()?.let { return it }
        }

        keys.find { key ->
            val publicId = PublicKeyIds.run { key.publicKeyId() }
            val thumbprint = key.getThumbprint()
            publicId == kid || thumbprint == kid ||
                kid.endsWith("#$publicId") || kid.endsWith("#$thumbprint") ||
                kid.endsWith("/$publicId") || kid.endsWith("/$thumbprint")
        }?.let { return it }

        throw IllegalArgumentException("Key ID '$kid' from JWS not found in DID document.")
    }

    private fun selectCrypto2VerificationKey(did: String, kid: String, keys: Set<Crypto2Key>): Crypto2Key {
        if (kid == did || kid.startsWith("$did#")) {
            val fragment = kid.substringAfter('#', missingDelimiterValue = "")
            if (fragment.isNotEmpty()) {
                keys.find { key ->
                    val keyId = key.id.value
                    !PublicKeyIds.isHttpKeyId(keyId) && (fragment == keyId)
                }?.let { return it }
            }
            keys.singleOrNull()?.let { return it }
        }

        keys.find { key ->
            val keyId = key.id.value
            !PublicKeyIds.isHttpKeyId(keyId) && (
                keyId == kid || kid.endsWith("#$keyId") || kid.endsWith("/$keyId")
                )
        }?.let { return it }

        throw IllegalArgumentException("Key ID '$kid' from JWS not found in DID document.")
    }
}
