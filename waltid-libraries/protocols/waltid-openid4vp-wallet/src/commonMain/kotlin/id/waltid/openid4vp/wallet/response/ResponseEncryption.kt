@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet.response

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto2.jose.JweContentEncryption
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwkOperation
import id.walt.crypto2.jose.JwkUse
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.serialization.BinaryData
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

object ResponseEncryption {
    private const val SUPPORTED_ALGORITHM = "ECDH-ES"

    @Deprecated("Use Crypto2Config")
    data class Config(
        val key: JWKKey,
        val encryptionMethod: String,
    ) {
        suspend fun thumbprintBytes(): ByteArray = key.getPublicKey().getThumbprint().decodeFromBase64Url()
    }

    data class Crypto2Config(
        val recipientPublicKey: EncodedKey.Jwk,
        val contentEncryption: JweContentEncryption,
    ) {
        init {
            require(!recipientPublicKey.privateMaterial) { "Verifier response-encryption JWK must be public only" }
            require(!Jwk.containsPrivateMaterial(Jwk.parse(recipientPublicKey))) {
                "Verifier response-encryption JWK must not contain private material"
            }
        }

        suspend fun thumbprint(): String = Jwk.sha256Thumbprint(recipientPublicKey)

        suspend fun thumbprintBytes(): ByteArray = thumbprint().decodeFromBase64Url()
    }

    @Deprecated("Use resolveCrypto2")
    @Suppress("DEPRECATION")
    suspend fun resolve(authorizationRequest: AuthorizationRequest): Config? {
        return resolveCrypto2(authorizationRequest)?.let { config ->
            Config(
                key = JWKKey.importJWK(config.recipientPublicKey.data.toByteArray().decodeToString()).getOrThrow(),
                encryptionMethod = config.contentEncryption.identifier,
            )
        }
    }

    suspend fun resolveCrypto2(authorizationRequest: AuthorizationRequest): Crypto2Config? {
        if (authorizationRequest.responseMode !in OpenID4VPResponseMode.ENCRYPTED_RESPONSES) return null

        // 1. Get Encryption Metadata
        val metadata = requireNotNull(authorizationRequest.clientMetadata) {
            "client_metadata is required for encrypted responses"
        }

        // 2. Select Verifier's Public Key
        // We prefer a key explicitly marked for encryption ('use': 'enc'), otherwise fall back to the first available key.
        val candidateKeys = metadata.jwks?.keys.orEmpty().filter { jwk ->
            jwk["alg"]?.jsonPrimitive?.contentOrNull == SUPPORTED_ALGORITHM &&
                jwk["use"]?.jsonPrimitive?.contentOrNull.let { it == null || it == "enc" }
        }
        candidateKeys.forEach { jwk ->
            require(!Jwk.containsPrivateMaterial(jwk)) {
                "Verifier response-encryption JWK must not contain private material"
            }
        }
        val verifierJwk = candidateKeys.firstOrNull {
            it["use"]?.jsonPrimitive?.contentOrNull == "enc" && isSupportedVerifierEncryptionJwk(it)
        } ?: candidateKeys.firstOrNull(::isSupportedVerifierEncryptionJwk)
            ?: throw IllegalArgumentException(
                "client_metadata.jwks must contain an encryption key with alg=$SUPPORTED_ALGORITHM"
            )

        // 3. Select Encryption Algorithm (enc)
        // Spec says default is A128GCM if not specified
        return Crypto2Config(
            recipientPublicKey = encodePublicJwk(verifierJwk),
            contentEncryption = selectContentEncryption(metadata.encryptedResponseEncValuesSupported),
        )
    }

    internal fun selectContentEncryption(advertised: List<String>?): JweContentEncryption {
        if (advertised == null) return JweContentEncryption.A128GCM
        return advertised.firstNotNullOfOrNull { identifier ->
            JweContentEncryption.entries.firstOrNull { it.identifier == identifier }
        } ?: throw IllegalArgumentException("Verifier does not support a compatible response content-encryption algorithm")
    }

    internal fun isSupportedVerifierEncryptionJwk(jwk: JsonObject): Boolean {
        val encoded = try {
            encodePublicJwk(jwk)
        } catch (_: IllegalArgumentException) {
            return false
        }
        val metadata = try {
            Jwk.metadata(encoded)
        } catch (_: IllegalArgumentException) {
            return false
        }
        val keyOperations = metadata.operations
        return jwk["kty"]?.jsonPrimitive?.contentOrNull == "EC" &&
            jwk["crv"]?.jsonPrimitive?.contentOrNull in setOf("P-256", "P-384", "P-521") &&
            metadata.algorithm == SUPPORTED_ALGORITHM &&
            !metadata.keyId.isNullOrBlank() &&
            (metadata.use == null || metadata.use == JwkUse.ENCRYPTION) &&
            (keyOperations == null || keyOperations.any {
                it == JwkOperation.DERIVE_KEY || it == JwkOperation.DERIVE_BITS
            })
    }

    private fun encodePublicJwk(jwk: JsonObject): EncodedKey.Jwk {
        require(!Jwk.containsPrivateMaterial(jwk)) {
            "Verifier response-encryption JWK must not contain private material"
        }
        return EncodedKey.Jwk(
            data = BinaryData(jwk.toString().encodeToByteArray()),
            privateMaterial = false,
        )
    }
}
