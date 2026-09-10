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

    /**
     * Immutable description of the response-encryption selection used by the wallet.
     *
     * The selected key is represented only by its protocol identifier and public-key
     * thumbprint. No key material is exposed through this model.
     *
     * @property keyManagementAlgorithm JWE `alg` value selected for the response.
     * @property contentEncryptionAlgorithm JWE `enc` value selected for the response.
     * @property verifierKeyId Verifier-provided identifier of the selected public key.
     * @property verifierKeyThumbprint RFC 7638 thumbprint of the selected public key.
     */
    data class Metadata(
        val keyManagementAlgorithm: String,
        val contentEncryptionAlgorithm: String,
        val verifierKeyId: String?,
        val verifierKeyThumbprint: String,
    )

    @Deprecated("Use Crypto2Config")
    data class Config(
        val key: JWKKey,
        val encryptionMethod: String,
    ) {
        /** Describes this selection without exposing the selected public key. */
        suspend fun metadata(): Metadata = Metadata(
            keyManagementAlgorithm = SUPPORTED_ALGORITHM,
            contentEncryptionAlgorithm = encryptionMethod,
            verifierKeyId = key.exportJWKObject()["kid"]?.jsonPrimitive?.contentOrNull,
            verifierKeyThumbprint = key.getPublicKey().getThumbprint().substringAfterLast(':'),
        )

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

        /** Describes this selection without exposing the selected public key. */
        suspend fun metadata(): Metadata = Metadata(
            keyManagementAlgorithm = SUPPORTED_ALGORITHM,
            contentEncryptionAlgorithm = contentEncryption.identifier,
            verifierKeyId = Jwk.parse(recipientPublicKey)["kid"]?.jsonPrimitive?.contentOrNull,
            verifierKeyThumbprint = thumbprint(),
        )
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
        // Accept keys explicitly marked for encryption or without a use value; deterministic
        // thumbprint/kid ordering selects one when more than one key is eligible.
        val keys = metadata.jwks?.keys.orEmpty()
        require(keys.isNotEmpty()) { "client_metadata.jwks must contain at least one response-encryption key" }
        val keyIds = keys.mapIndexed { index, jwk ->
            (jwk["kid"] as? kotlinx.serialization.json.JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalArgumentException(
                    "Every JWK in client_metadata.jwks must have a non-blank string kid (invalid key at index $index)"
                )
        }
        require(keyIds.size == keyIds.toSet().size) {
            "Every JWK in client_metadata.jwks must have a unique kid"
        }
        keys.forEach { jwk ->
            require(!Jwk.containsPrivateMaterial(jwk)) {
                "Verifier response-encryption JWK must not contain private material"
            }
        }

        val candidateKeys = keys.filter { jwk ->
            jwk["alg"]?.jsonPrimitive?.contentOrNull == SUPPORTED_ALGORITHM &&
                jwk["use"]?.jsonPrimitive?.contentOrNull.let { it == null || it == "enc" }
        }
        val verifierJwk = candidateKeys
            .filter(::isSupportedVerifierEncryptionJwk)
            .map { jwk -> jwk to Jwk.sha256Thumbprint(encodePublicJwk(jwk)) }
            .sortedWith(compareBy<Pair<JsonObject, String>>({ it.second }, { it.first["kid"]!!.jsonPrimitive.content }))
            .firstOrNull()?.first
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
        require(advertised.isNotEmpty()) {
            "encrypted_response_enc_values_supported must not be empty"
        }
        return listOf(JweContentEncryption.A256GCM, JweContentEncryption.A128GCM)
            .firstOrNull { candidate -> candidate.identifier in advertised }
            ?: throw IllegalArgumentException("Verifier does not support a compatible response content-encryption algorithm")
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
