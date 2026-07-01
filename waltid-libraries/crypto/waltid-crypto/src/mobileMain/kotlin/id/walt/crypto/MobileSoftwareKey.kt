package id.walt.crypto

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.CryptographyProviderApi
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.EdDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUtils
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonCanonicalizationUtils
import id.walt.crypto.utils.JwsUtils.decodeJwsStrings
import id.walt.crypto.utils.ShaUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

internal sealed class MobileSoftwareKey(
    private val kid: String,
) : Key() {

    companion object {
        suspend fun create(provider: CryptographyProvider, kid: String, keyType: KeyType): MobileSoftwareKey =
            when (keyType) {
                KeyType.Ed25519 -> provider.get(EdDSA)
                    .keyPairGenerator(EdDSA.Curve.Ed25519)
                    .generateKey()
                    .let { keyPair -> Ed25519(kid, keyPair.publicKey, keyPair.privateKey) }
                KeyType.secp256k1 -> provider.get(ECDSA)
                    .keyPairGenerator(EC.Curve.secp256k1)
                    .generateKey()
                    .let { keyPair -> Secp256k1(kid, keyPair.publicKey, keyPair.privateKey) }
                else -> error("Unsupported software key type: $keyType")
            }

        @OptIn(CryptographyProviderApi::class)
        suspend fun load(provider: CryptographyProvider, kid: String, keyType: KeyType, jwkBytes: ByteArray): MobileSoftwareKey {
            val hasPrivateMaterial = Json.parseToJsonElement(jwkBytes.decodeToString()).jsonObject.containsKey("d")
            return when (keyType) {
                KeyType.Ed25519 -> {
                    val edDsa = provider.get(EdDSA)
                    Ed25519(
                        kid = kid,
                        publicKey = edDsa.publicKeyDecoder(EdDSA.Curve.Ed25519)
                            .decodeFromByteArray(EdDSA.PublicKey.Format.JWK, jwkBytes),
                        privateKey = if (hasPrivateMaterial) {
                            edDsa.privateKeyDecoder(EdDSA.Curve.Ed25519)
                                .decodeFromByteArray(EdDSA.PrivateKey.Format.JWK, jwkBytes)
                        } else {
                            null
                        },
                    )
                }
                KeyType.secp256k1 -> {
                    val ecdsa = provider.get(ECDSA)
                    Secp256k1(
                        kid = kid,
                        publicKey = ecdsa.publicKeyDecoder(EC.Curve.secp256k1)
                            .decodeFromByteArray(EC.PublicKey.Format.JWK, jwkBytes),
                        privateKey = if (hasPrivateMaterial) {
                            ecdsa.privateKeyDecoder(EC.Curve.secp256k1)
                                .decodeFromByteArray(EC.PrivateKey.Format.JWK, jwkBytes)
                        } else {
                            null
                        },
                    )
                }
                else -> error("Unsupported software key type: $keyType")
            }
        }
    }

    override suspend fun getKeyId(): String = kid

    override suspend fun getThumbprint(): String {
        val canonical = JsonCanonicalizationUtils.convertToRequiredMembersJsonString(this)
        return ShaUtils.sha256(canonical.encodeToByteArray()).encodeToBase64Url()
    }

    override suspend fun exportJWK(): String = exportJWKObject().toString()

    override suspend fun exportJWKObject(): JsonObject {
        val jwk = Json.parseToJsonElement(publicKeyJwkBytes().decodeToString()).jsonObject.toMutableMap()
        jwk["kid"] = JsonPrimitive(kid)
        return JsonObject(jwk)
    }

    override suspend fun exportPEM(): String = publicKeyPemBytes().decodeToString()

    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
        val (header, payload, signable) = KeyUtils.rawSignaturePayloadForJws(plaintext, headers, keyType)
        val signature = signRawBytes(signable)
        return KeyUtils.signJwsWithRawSignature(signature, header, payload)
    }

    override suspend fun verifyRaw(
        signed: ByteArray,
        detachedPlaintext: ByteArray?,
        customSignatureAlgorithm: String?,
    ): Result<ByteArray> = runCatching {
        val plaintext = requireNotNull(detachedPlaintext) { "Detached plaintext required" }
        verifySignature(plaintext, signed)
        plaintext
    }

    override suspend fun verifyJws(signedJws: String): Result<JsonElement> = runCatching {
        val parts = signedJws.decodeJwsStrings()
        val signedData = parts.getSignable().encodeToByteArray()
        verifySignature(signedData, parts.signature.decodeFromBase64Url())
        Json.parseToJsonElement(parts.payload.decodeFromBase64Url().decodeToString())
    }

    override suspend fun getPublicKey(): Key = JWKKey(exportJWK(), kid)

    override suspend fun getPublicKeyRepresentation(): ByteArray = publicKeyRawBytes()

    override suspend fun getMeta(): KeyMeta = JwkKeyMeta(getKeyId())

    override suspend fun deleteKey(): Boolean = true

    abstract suspend fun exportPrivateKeyMaterial(): ByteArray
    abstract suspend fun signRawBytes(plaintext: ByteArray, customSignatureAlgorithm: String? = null): ByteArray

    override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): Any =
        signRawBytes(plaintext, customSignatureAlgorithm)

    protected abstract suspend fun publicKeyJwkBytes(): ByteArray
    protected abstract suspend fun publicKeyPemBytes(): ByteArray
    protected abstract suspend fun publicKeyRawBytes(): ByteArray
    protected abstract suspend fun verifySignature(data: ByteArray, signature: ByteArray)

    private class Ed25519(
        kid: String,
        private val publicKey: EdDSA.PublicKey,
        private val privateKey: EdDSA.PrivateKey?,
    ) : MobileSoftwareKey(kid) {
        override val keyType: KeyType = KeyType.Ed25519
        override val hasPrivateKey: Boolean get() = privateKey != null

        override suspend fun publicKeyJwkBytes(): ByteArray =
            publicKey.encodeToByteArray(EdDSA.PublicKey.Format.JWK)

        override suspend fun publicKeyPemBytes(): ByteArray =
            publicKey.encodeToByteArray(EdDSA.PublicKey.Format.PEM)

        override suspend fun publicKeyRawBytes(): ByteArray =
            publicKey.encodeToByteArray(EdDSA.PublicKey.Format.RAW)

        override suspend fun signRawBytes(plaintext: ByteArray, customSignatureAlgorithm: String?): ByteArray =
            requireNotNull(privateKey) { "Only private key can do signing." }
                .signatureGenerator()
                .generateSignature(plaintext)

        override suspend fun verifySignature(data: ByteArray, signature: ByteArray) {
            publicKey.signatureVerifier().verifySignature(data, signature)
        }

        override suspend fun exportPrivateKeyMaterial(): ByteArray {
            check(hasPrivateKey) { "Only private key material can be exported." }
            return requireNotNull(privateKey).encodeToByteArray(EdDSA.PrivateKey.Format.JWK)
        }
    }

    private class Secp256k1(
        kid: String,
        private val publicKey: ECDSA.PublicKey,
        private val privateKey: ECDSA.PrivateKey?,
    ) : MobileSoftwareKey(kid) {
        override val keyType: KeyType = KeyType.secp256k1
        override val hasPrivateKey: Boolean get() = privateKey != null

        override suspend fun publicKeyJwkBytes(): ByteArray =
            publicKey.encodeToByteArray(EC.PublicKey.Format.JWK)

        override suspend fun publicKeyPemBytes(): ByteArray =
            publicKey.encodeToByteArray(EC.PublicKey.Format.PEM)

        override suspend fun publicKeyRawBytes(): ByteArray =
            publicKey.encodeToByteArray(EC.PublicKey.Format.RAW)

        override suspend fun signRawBytes(plaintext: ByteArray, customSignatureAlgorithm: String?): ByteArray =
            requireNotNull(privateKey) { "Only private key can do signing." }
                .signatureGenerator(SHA256, ECDSA.SignatureFormat.RAW)
                .generateSignature(plaintext)

        override suspend fun verifySignature(data: ByteArray, signature: ByteArray) {
            publicKey.signatureVerifier(SHA256, ECDSA.SignatureFormat.RAW).verifySignature(data, signature)
        }

        override suspend fun exportPrivateKeyMaterial(): ByteArray {
            check(hasPrivateKey) { "Only private key material can be exported." }
            return requireNotNull(privateKey).encodeToByteArray(EC.PrivateKey.Format.JWK)
        }
    }
}
