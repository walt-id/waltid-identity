@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.crypto2.jose.CompactJws
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext
import id.walt.verifier.openid.models.authorization.ClientMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Handles a pre-registered client (no prefix) per OpenID4VP 1.0, Section 5.9.2.
 */
@Serializable
data class PreRegistered(override val rawValue: String) : ClientId {

    suspend fun authenticatePreRegistered(
        clientId: PreRegistered,
        preRegisteredMetadataProvider: suspend (String) -> String?,
    ): ClientValidationResult = authenticatePreRegistered(
        clientId,
        RequestContext(clientId.rawValue),
        preRegisteredMetadataProvider,
    )

    suspend fun authenticatePreRegistered(
        clientId: PreRegistered,
        context: RequestContext,
        preRegisteredMetadataProvider: suspend (String) -> String?
    ): ClientValidationResult {
        // library expects the calling layer to provide the metadata
        val metadataJson = preRegisteredMetadataProvider(clientId.rawValue)
            ?: return ClientValidationResult.Failure(ClientIdError.PreRegisteredClientNotFound(clientId.rawValue))

        val metadata = ClientMetadata.fromJson(metadataJson).getOrElse {
            return ClientValidationResult.Failure(
                ClientIdError.InvalidMetadata("Stored metadata is malformed: ${it.message}")
            )
        }
        val requestObject = context.requestObjectJws
            ?: return ClientValidationResult.Failure(ClientIdError.MissingRequestObject)
        val verificationKeys = metadata.jwks?.keys.orEmpty().filter(JsonObject::isVerificationKey)
        if (verificationKeys.isEmpty()) {
            return ClientValidationResult.Failure(
                ClientIdError.InvalidMetadata("Stored metadata contains no request-object verification keys")
            )
        }
        val decoded = try {
            CompactJws.decodeUnverified(requestObject)
        } catch (_: IllegalArgumentException) {
            return ClientValidationResult.Failure(ClientIdError.InvalidJws)
        }
        val keyId = decoded.protectedHeader["kid"]?.jsonPrimitive?.contentOrNull
        val candidates = keyId?.let { expected ->
            verificationKeys.filter { it["kid"]?.jsonPrimitive?.contentOrNull == expected }
        } ?: verificationKeys
        for ((index, jwk) in candidates.withIndex()) {
            try {
                ClientIdCrypto2.verify(
                    requestObject,
                    ClientIdCrypto2.keyFromJwk(jwk, "${clientId.rawValue}#$index"),
                )
                return ClientValidationResult.Success(metadata)
            } catch (cause: CancellationException) {
                throw cause
            } catch (_: Throwable) {
                // Try the next trusted verification key.
            }
        }
        return ClientValidationResult.Failure(ClientIdError.InvalidSignature)
    }

}

private fun JsonObject.isVerificationKey(): Boolean {
    if (this["use"]?.jsonPrimitive?.contentOrNull == "enc") return false
    val operations = this["key_ops"] as? JsonArray ?: return true
    return operations.any { it.jsonPrimitive.contentOrNull == "verify" }
}
