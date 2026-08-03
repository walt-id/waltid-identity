@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet.response

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.JsonCanonicalizationUtils
import id.walt.crypto.utils.ShaUtils
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Shared response-encryption negotiation and JWE implementation for wallet2 and mobile. */
object ResponseEncryption {
    private const val SUPPORTED_ALGORITHM = "ECDH-ES"
    private val supportedEncryptionMethods = listOf("A256GCM", "A128GCM")

    /** Immutable, key-material-free description exposed to wallet UIs. */
    data class Metadata(
        val keyManagementAlgorithm: String,
        val contentEncryptionAlgorithm: String,
        val verifierKeyId: String?,
        val verifierKeyThumbprint: String,
    )

    /** The complete authenticated selection reused for mdoc and final response encryption. */
    data class Config(
        val verifierJwk: JsonObject,
        val verifierKey: JWKKey,
        val keyId: String,
        val encAlgorithm: String,
        val algAlgorithm: String,
        val verifierKeyThumbprint: String,
    ) {
        suspend fun metadata(): Metadata = Metadata(
            keyManagementAlgorithm = algAlgorithm,
            contentEncryptionAlgorithm = encAlgorithm,
            verifierKeyId = keyId,
            verifierKeyThumbprint = verifierKeyThumbprint,
        )

        suspend fun thumbprintBytes(): ByteArray = verifierKeyThumbprint.decodeFromBase64Url()
    }

    /** Selects one deterministic verifier key and content-encryption algorithm. */
    suspend fun resolve(authorizationRequest: AuthorizationRequest): Config? {
        if (authorizationRequest.responseMode !in OpenID4VPResponseMode.ENCRYPTED_RESPONSES) return null

        val clientMetadata = requireNotNull(authorizationRequest.clientMetadata) {
            "client_metadata is required for encrypted responses"
        }
        val keys = clientMetadata.jwks?.keys.orEmpty()
        require(keys.isNotEmpty()) { "No encryption keys found in client_metadata.jwks" }

        val keyIds = keys.mapIndexed { index, jwk ->
            (jwk["kid"] as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException(
                    "Every JWK in client_metadata.jwks must have a non-blank string kid (invalid key at index $index)"
                )
        }
        require(keyIds.size == keyIds.toSet().size) {
            "Every JWK in client_metadata.jwks must have a request-unique kid"
        }

        val selected = keys
            .filter(::isSupportedEncryptionKey)
            .map { jwk ->
                val key = JWKKey.importJWK(jwk.toString()).getOrThrow()
                val thumbprint = ShaUtils.calculateSha256Base64Url(
                    JsonCanonicalizationUtils.convertToRequiredMembersJsonString(key)
                )
                SelectedKey(jwk, key, thumbprint)
            }
            .sortedWith(compareBy<SelectedKey>({ it.thumbprint }, { it.keyId }))
            .firstOrNull()
            ?: throw IllegalArgumentException(
                "No supported encryption JWK (EC/P-256/$SUPPORTED_ALGORITHM) found in client_metadata.jwks"
            )

        val advertisedEnc = clientMetadata.encryptedResponseEncValuesSupported
        val encAlgorithm = if (advertisedEnc == null) {
            "A128GCM"
        } else {
            require(advertisedEnc.isNotEmpty()) {
                "encrypted_response_enc_values_supported must not be empty"
            }
            supportedEncryptionMethods.firstOrNull { it in advertisedEnc }
                ?: throw IllegalArgumentException("No supported content encryption algorithm was advertised")
        }

        return Config(
            verifierJwk = selected.jwk,
            verifierKey = selected.key,
            keyId = selected.keyId,
            encAlgorithm = encAlgorithm,
            algAlgorithm = SUPPORTED_ALGORITHM,
            verifierKeyThumbprint = selected.thumbprint,
        )
    }

    /** Encrypts an authorization response with the exact selection returned by [resolve]. */
    suspend fun encryptResponse(payload: JsonObject, config: Config): String {
        require(config.algAlgorithm == SUPPORTED_ALGORITHM) {
            "Unsupported JWE alg ${config.algAlgorithm}"
        }
        require(config.verifierJwk["kid"]?.jsonPrimitive?.contentOrNull == config.keyId) {
            "Encryption configuration kid does not match the selected verifier JWK"
        }
        return config.verifierKey.encryptJwe(
            payload.toString().encodeToByteArray(),
            config.encAlgorithm,
        )
    }

    private data class SelectedKey(
        val jwk: JsonObject,
        val key: JWKKey,
        val thumbprint: String,
    ) {
        val keyId: String get() = jwk["kid"]!!.jsonPrimitive.content
    }

    private fun isSupportedEncryptionKey(key: JsonObject): Boolean {
        val use = key["use"]?.jsonPrimitive?.contentOrNull
        return key["alg"]?.jsonPrimitive?.contentOrNull == SUPPORTED_ALGORITHM &&
            key["kty"]?.jsonPrimitive?.contentOrNull == "EC" &&
            key["crv"]?.jsonPrimitive?.contentOrNull == "P-256" &&
            (use == null || use == "enc") &&
            key["x"]?.jsonPrimitive?.contentOrNull != null &&
            key["y"]?.jsonPrimitive?.contentOrNull != null
    }
}
