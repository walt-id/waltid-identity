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

internal class MobileSoftwareKey private constructor(
    private val kid: String,
    override val keyType: KeyType,
    private val edPublicKey: EdDSA.PublicKey?,
    private val edPrivateKey: EdDSA.PrivateKey?,
    private val ecPublicKey: ECDSA.PublicKey?,
    private val ecPrivateKey: ECDSA.PrivateKey?,
) : Key() {

    companion object {
        suspend fun create(provider: CryptographyProvider, kid: String, keyType: KeyType): MobileSoftwareKey {
            val edDsa by lazy { provider.get(EdDSA) }
            val ecdsa by lazy { provider.get(ECDSA) }
            return when (keyType) {
                KeyType.Ed25519 -> {
                    val keyPair = edDsa.keyPairGenerator(EdDSA.Curve.Ed25519).generateKey()
                    MobileSoftwareKey(
                        kid = kid,
                        keyType = keyType,
                        edPublicKey = keyPair.publicKey,
                        edPrivateKey = keyPair.privateKey,
                        ecPublicKey = null,
                        ecPrivateKey = null,
                    )
                }
                KeyType.secp256k1 -> {
                    val keyPair = ecdsa.keyPairGenerator(EC.Curve.secp256k1).generateKey()
                    MobileSoftwareKey(
                        kid = kid,
                        keyType = keyType,
                        edPublicKey = null,
                        edPrivateKey = null,
                        ecPublicKey = keyPair.publicKey,
                        ecPrivateKey = keyPair.privateKey,
                    )
                }
                else -> error("Unsupported software key type: $keyType")
            }
        }

        @OptIn(CryptographyProviderApi::class)
        suspend fun load(provider: CryptographyProvider, kid: String, keyType: KeyType, jwkBytes: ByteArray): MobileSoftwareKey {
            val hasPrivateMaterial = Json.parseToJsonElement(jwkBytes.decodeToString()).jsonObject.containsKey("d")
            val edDsa by lazy { provider.get(EdDSA) }
            val ecdsa by lazy { provider.get(ECDSA) }
            return when (keyType) {
                KeyType.Ed25519 -> {
                    val publicKey = edDsa.publicKeyDecoder(EdDSA.Curve.Ed25519)
                        .decodeFromByteArray(EdDSA.PublicKey.Format.JWK, jwkBytes)
                    val privateKey = if (hasPrivateMaterial) {
                        edDsa.privateKeyDecoder(EdDSA.Curve.Ed25519)
                            .decodeFromByteArray(EdDSA.PrivateKey.Format.JWK, jwkBytes)
                    } else {
                        null
                    }
                    MobileSoftwareKey(
                        kid = kid,
                        keyType = keyType,
                        edPublicKey = publicKey,
                        edPrivateKey = privateKey,
                        ecPublicKey = null,
                        ecPrivateKey = null,
                    )
                }
                KeyType.secp256k1 -> {
                    val publicKey = ecdsa.publicKeyDecoder(EC.Curve.secp256k1)
                        .decodeFromByteArray(EC.PublicKey.Format.JWK, jwkBytes)
                    val privateKey = if (hasPrivateMaterial) {
                        ecdsa.privateKeyDecoder(EC.Curve.secp256k1)
                            .decodeFromByteArray(EC.PrivateKey.Format.JWK, jwkBytes)
                    } else {
                        null
                    }
                    MobileSoftwareKey(
                        kid = kid,
                        keyType = keyType,
                        edPublicKey = null,
                        edPrivateKey = null,
                        ecPublicKey = publicKey,
                        ecPrivateKey = privateKey,
                    )
                }
                else -> error("Unsupported software key type: $keyType")
            }
        }
    }

    override val hasPrivateKey: Boolean
        get() = when (keyType) {
            KeyType.Ed25519 -> edPrivateKey != null
            KeyType.secp256k1 -> ecPrivateKey != null
            else -> error("Unexpected key type: $keyType")
        }

    override suspend fun getKeyId(): String = kid

    override suspend fun getThumbprint(): String {
        val canonical = JsonCanonicalizationUtils.convertToRequiredMembersJsonString(this)
        return ShaUtils.sha256(canonical.encodeToByteArray()).encodeToBase64Url()
    }

    override suspend fun exportJWK(): String = exportJWKObject().toString()

    override suspend fun exportJWKObject(): JsonObject {
        val jwkBytes = when (keyType) {
            KeyType.Ed25519 -> requireNotNull(edPublicKey)
                .encodeToByteArray(EdDSA.PublicKey.Format.JWK)
            KeyType.secp256k1 -> requireNotNull(ecPublicKey)
                .encodeToByteArray(EC.PublicKey.Format.JWK)
            else -> error("Unexpected key type: $keyType")
        }
        val jwk = Json.parseToJsonElement(jwkBytes.decodeToString()).jsonObject.toMutableMap()
        jwk["kid"] = JsonPrimitive(kid)
        return JsonObject(jwk)
    }

    override suspend fun exportPEM(): String {
        val pemBytes = when (keyType) {
            KeyType.Ed25519 -> requireNotNull(edPublicKey)
                .encodeToByteArray(EdDSA.PublicKey.Format.PEM)
            KeyType.secp256k1 -> requireNotNull(ecPublicKey)
                .encodeToByteArray(EC.PublicKey.Format.PEM)
            else -> error("Unexpected key type: $keyType")
        }
        return pemBytes.decodeToString()
    }

    override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): ByteArray = when (keyType) {
        KeyType.Ed25519 -> requireNotNull(edPrivateKey) { "Only private key can do signing." }
            .signatureGenerator()
            .generateSignature(plaintext)
        KeyType.secp256k1 -> requireNotNull(ecPrivateKey) { "Only private key can do signing." }
            .signatureGenerator(SHA256, ECDSA.SignatureFormat.RAW)
            .generateSignature(plaintext)
        else -> error("Unexpected key type: $keyType")
    }

    override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
        val (header, payload, signable) = KeyUtils.rawSignaturePayloadForJws(plaintext, headers, keyType)
        val signature = signRaw(signable)
        return KeyUtils.signJwsWithRawSignature(signature, header, payload)
    }

    override suspend fun verifyRaw(
        signed: ByteArray,
        detachedPlaintext: ByteArray?,
        customSignatureAlgorithm: String?,
    ): Result<ByteArray> = runCatching {
        val plaintext = requireNotNull(detachedPlaintext) { "Detached plaintext required" }
        when (keyType) {
            KeyType.Ed25519 -> requireNotNull(edPublicKey)
                .signatureVerifier()
                .verifySignature(plaintext, signed)
            KeyType.secp256k1 -> requireNotNull(ecPublicKey)
                .signatureVerifier(SHA256, ECDSA.SignatureFormat.RAW)
                .verifySignature(plaintext, signed)
            else -> error("Unexpected key type: $keyType")
        }
        plaintext
    }

    override suspend fun verifyJws(signedJws: String): Result<JsonElement> = runCatching {
        val parts = signedJws.decodeJwsStrings()
        val signedData = parts.getSignable().encodeToByteArray()
        val signature = parts.signature.decodeFromBase64Url()
        when (keyType) {
            KeyType.Ed25519 -> requireNotNull(edPublicKey)
                .signatureVerifier()
                .verifySignature(signedData, signature)
            KeyType.secp256k1 -> requireNotNull(ecPublicKey)
                .signatureVerifier(SHA256, ECDSA.SignatureFormat.RAW)
                .verifySignature(signedData, signature)
            else -> error("Unexpected key type: $keyType")
        }
        Json.parseToJsonElement(parts.payload.decodeFromBase64Url().decodeToString())
    }

    override suspend fun getPublicKey(): Key = JWKKey(exportJWK(), kid)

    override suspend fun getPublicKeyRepresentation(): ByteArray = when (keyType) {
        KeyType.Ed25519 -> requireNotNull(edPublicKey)
            .encodeToByteArray(EdDSA.PublicKey.Format.RAW)
        KeyType.secp256k1 -> requireNotNull(ecPublicKey)
            .encodeToByteArray(EC.PublicKey.Format.RAW)
        else -> error("Unexpected key type: $keyType")
    }

    override suspend fun getMeta(): KeyMeta = JwkKeyMeta(getKeyId())

    override suspend fun deleteKey(): Boolean = true

    suspend fun exportPrivateKeyMaterial(): ByteArray {
        check(hasPrivateKey) { "Only private key material can be exported." }
        return when (keyType) {
            KeyType.Ed25519 -> requireNotNull(edPrivateKey)
                .encodeToByteArray(EdDSA.PrivateKey.Format.JWK)
            KeyType.secp256k1 -> requireNotNull(ecPrivateKey)
                .encodeToByteArray(EC.PrivateKey.Format.JWK)
            else -> error("Unexpected key type: $keyType")
        }
    }
}
