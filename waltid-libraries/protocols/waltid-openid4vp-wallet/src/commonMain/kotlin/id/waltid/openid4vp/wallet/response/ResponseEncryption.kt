@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet.response

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object ResponseEncryption {
    private const val SUPPORTED_ALGORITHM = "ECDH-ES"
    private val supportedEncryptionMethods = setOf("A128GCM", "A256GCM")

    data class Config(
        val key: JWKKey,
        val encryptionMethod: String,
    ) {
        suspend fun thumbprintBytes(): ByteArray = key.getPublicKey().getThumbprint().decodeFromBase64Url()
    }

    suspend fun resolve(authorizationRequest: AuthorizationRequest): Config? {
        if (authorizationRequest.responseMode !in OpenID4VPResponseMode.ENCRYPTED_RESPONSES) return null

        val metadata = requireNotNull(authorizationRequest.clientMetadata) {
            "client_metadata is required for encrypted responses"
        }
        val keyData = metadata.jwks?.keys
            ?.firstOrNull { jwk ->
                jwk["alg"]?.jsonPrimitive?.contentOrNull == SUPPORTED_ALGORITHM &&
                    jwk["use"]?.jsonPrimitive?.contentOrNull.let { it == null || it == "enc" }
            }
            ?: throw IllegalArgumentException(
                "client_metadata.jwks must contain an encryption key with alg=$SUPPORTED_ALGORITHM"
            )
        val encryptionMethod = metadata.encryptedResponseEncValuesSupported
            ?.firstOrNull { it in supportedEncryptionMethods }
            ?: metadata.encryptedResponseEncValuesSupported
                ?.let { throw IllegalArgumentException("No supported encrypted response enc value: $it") }
            ?: "A128GCM"

        return Config(
            key = JWKKey.importJWK(keyData.toString()).getOrThrow(),
            encryptionMethod = encryptionMethod,
        )
    }
}
