package id.walt.crypto

import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.josef.toJsonWebKey
import at.asitplus.signum.supreme.SignatureResult
import at.asitplus.signum.supreme.os.AndroidKeyStoreProvider
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.CryptographyProviderApi
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.EdDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.providers.jdk.JDK
import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyMeta
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUtils
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
import org.bouncycastle.jce.provider.BouncyCastleProvider
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

sealed class AndroidKey : Key() {

    class Options(
        val kid: String = Uuid.random().toString(),
        val keyType: KeyType,
    )

    class Platform internal constructor(private val options: Options) : AndroidKey() {

        companion object {
            suspend fun create(options: Options): Platform {
                when (val curve = options.keyType.toPlatformKeyStoreCurve()) {
                    null -> AndroidKeyStoreProvider.createSigningKey(options.kid) {
                        rsa { }
                    }.getOrThrow()
                    else -> AndroidKeyStoreProvider.createSigningKey(options.kid) {
                        ec { this.curve = curve }
                    }.getOrThrow()
                }
                return Platform(options)
            }

            suspend fun load(options: Options): Platform {
                AndroidKeyStoreProvider.getSignerForKey(options.kid).getOrThrow()
                return Platform(options)
            }

            suspend fun delete(kid: String) {
                AndroidKeyStoreProvider.deleteSigningKey(kid).getOrThrow()
            }
        }

        override val keyType get() = options.keyType
        override val hasPrivateKey = true

        private suspend fun signer() = AndroidKeyStoreProvider.getSignerForKey(options.kid).getOrThrow()

        override suspend fun getKeyId(): String = options.kid

        override suspend fun getThumbprint(): String =
            signer().publicKey.toJsonWebKey(options.kid).jwkThumbprint

        override suspend fun exportJWK(): String = exportJWKObject().toString()

        override suspend fun exportJWKObject(): JsonObject {
            val jwkStr = joseCompliantSerializer.encodeToString(signer().publicKey.toJsonWebKey(options.kid))
            return Json.parseToJsonElement(jwkStr) as JsonObject
        }

        override suspend fun exportPEM(): String {
            val derBytes = signer().publicKey.encodeToTlv().derEncoded
            val base64 = Base64.Mime.encode(derBytes)
            return "-----BEGIN PUBLIC KEY-----\n$base64\n-----END PUBLIC KEY-----"
        }

        override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): ByteArray {
            val result = signer().sign(plaintext)
            check(result is SignatureResult.Success) { "Signing failed: $result" }
            return result.signature.rawByteArray
        }

        override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
            val signer = signer()
            return signJwsWithPlatformSigner(options.keyType, plaintext, headers) { data -> signer.sign(data) }
        }

        override suspend fun verifyRaw(
            signed: ByteArray, detachedPlaintext: ByteArray?, customSignatureAlgorithm: String?
        ): Result<ByteArray> {
            val signer = signer()
            return verifyRawWithPlatformSigner(options.keyType, signer.publicKey, signed, detachedPlaintext)
        }

        override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
            val signer = signer()
            return verifyJwsWithPlatformSigner(options.keyType, signer.publicKey, signedJws)
        }

        override suspend fun getPublicKey(): Key = Platform(options)
        override suspend fun getPublicKeyRepresentation(): ByteArray = signer().publicKey.encodeToTlv().derEncoded
        override suspend fun getMeta(): KeyMeta = JwkKeyMeta(getKeyId())
        override suspend fun deleteKey(): Boolean = runCatching {
            AndroidKeyStoreProvider.deleteSigningKey(options.kid).getOrThrow()
        }.isSuccess
    }

    class Software private constructor(
        private val options: Options,
        private val edKeyPair: EdDSA.KeyPair?,
        private val ecKeyPair: ECDSA.KeyPair?,
    ) : AndroidKey() {

        companion object {
            private val provider by lazy {
                CryptographyProvider.JDK(BouncyCastleProvider())
            }

            private val edDsa: EdDSA by lazy { provider.get(EdDSA) }
            private val ecdsa: ECDSA by lazy { provider.get(ECDSA) }

            suspend fun create(options: Options): Software = when (options.keyType) {
                KeyType.Ed25519 -> {
                    val keyPair = edDsa.keyPairGenerator(EdDSA.Curve.Ed25519).generateKey()
                    Software(options, edKeyPair = keyPair, ecKeyPair = null)
                }
                KeyType.secp256k1 -> {
                    val keyPair = ecdsa.keyPairGenerator(EC.Curve.secp256k1).generateKey()
                    Software(options, edKeyPair = null, ecKeyPair = keyPair)
                }
                else -> error("Unsupported software key type: ${options.keyType}")
            }

            @OptIn(CryptographyProviderApi::class)
            suspend fun load(options: Options, jwkBytes: ByteArray): Software = when (options.keyType) {
                KeyType.Ed25519 -> {
                    val privateKey = edDsa.privateKeyDecoder(EdDSA.Curve.Ed25519)
                        .decodeFromByteArray(EdDSA.PrivateKey.Format.JWK, jwkBytes)
                    val publicKey = edDsa.publicKeyDecoder(EdDSA.Curve.Ed25519)
                        .decodeFromByteArray(EdDSA.PublicKey.Format.JWK, jwkBytes)
                    val keyPair = object : EdDSA.KeyPair {
                        override val publicKey = publicKey
                        override val privateKey = privateKey
                    }
                    Software(options, edKeyPair = keyPair, ecKeyPair = null)
                }
                KeyType.secp256k1 -> {
                    val privateKey = ecdsa.privateKeyDecoder(EC.Curve.secp256k1)
                        .decodeFromByteArray(EC.PrivateKey.Format.JWK, jwkBytes)
                    val publicKey = ecdsa.publicKeyDecoder(EC.Curve.secp256k1)
                        .decodeFromByteArray(EC.PublicKey.Format.JWK, jwkBytes)
                    val keyPair = object : ECDSA.KeyPair {
                        override val publicKey = publicKey
                        override val privateKey = privateKey
                    }
                    Software(options, edKeyPair = null, ecKeyPair = keyPair)
                }
                else -> error("Unsupported software key type: ${options.keyType}")
            }

            suspend fun exportKeyMaterial(key: Software): ByteArray = when (key.keyType) {
                KeyType.Ed25519 -> key.edKeyPair!!.privateKey.encodeToByteArray(EdDSA.PrivateKey.Format.JWK)
                KeyType.secp256k1 -> key.ecKeyPair!!.privateKey.encodeToByteArray(EC.PrivateKey.Format.JWK)
                else -> error("Unexpected key type: ${key.keyType}")
            }
        }

        override val keyType get() = options.keyType
        override val hasPrivateKey = true

        override suspend fun getKeyId(): String = options.kid

        override suspend fun getThumbprint(): String {
            val canonical = JsonCanonicalizationUtils.convertToRequiredMembersJsonString(this)
            return ShaUtils.sha256(canonical.encodeToByteArray()).encodeToBase64Url()
        }

        override suspend fun exportJWK(): String = exportJWKObject().toString()

        override suspend fun exportJWKObject(): JsonObject {
            val jwkBytes = when (keyType) {
                KeyType.Ed25519 -> edKeyPair!!.publicKey.encodeToByteArray(EdDSA.PublicKey.Format.JWK)
                KeyType.secp256k1 -> ecKeyPair!!.publicKey.encodeToByteArray(EC.PublicKey.Format.JWK)
                else -> error("Unexpected key type: $keyType")
            }
            val jwk = Json.parseToJsonElement(jwkBytes.decodeToString()).jsonObject.toMutableMap()
            jwk["kid"] = JsonPrimitive(options.kid)
            return JsonObject(jwk)
        }

        override suspend fun exportPEM(): String {
            val pemBytes = when (keyType) {
                KeyType.Ed25519 -> edKeyPair!!.publicKey.encodeToByteArray(EdDSA.PublicKey.Format.PEM)
                KeyType.secp256k1 -> ecKeyPair!!.publicKey.encodeToByteArray(EC.PublicKey.Format.PEM)
                else -> error("Unexpected key type: $keyType")
            }
            return pemBytes.decodeToString()
        }

        override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): ByteArray = when (keyType) {
            KeyType.Ed25519 -> edKeyPair!!.privateKey.signatureGenerator().generateSignature(plaintext)
            KeyType.secp256k1 -> ecKeyPair!!.privateKey.signatureGenerator(SHA256, ECDSA.SignatureFormat.RAW).generateSignature(plaintext)
            else -> error("Unexpected key type: $keyType")
        }

        override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
            val (header, payload, signable) = KeyUtils.rawSignaturePayloadForJws(plaintext, headers, keyType)
            val signature = signRaw(signable)
            return KeyUtils.signJwsWithRawSignature(signature, header, payload)
        }

        override suspend fun verifyRaw(
            signed: ByteArray, detachedPlaintext: ByteArray?, customSignatureAlgorithm: String?
        ): Result<ByteArray> = runCatching {
            val plaintext = requireNotNull(detachedPlaintext) { "Detached plaintext required" }
            when (keyType) {
                KeyType.Ed25519 -> edKeyPair!!.publicKey.signatureVerifier().verifySignature(plaintext, signed)
                KeyType.secp256k1 -> ecKeyPair!!.publicKey.signatureVerifier(SHA256, ECDSA.SignatureFormat.RAW).verifySignature(plaintext, signed)
                else -> error("Unexpected key type: $keyType")
            }
            plaintext
        }

        override suspend fun verifyJws(signedJws: String): Result<JsonElement> = runCatching {
            val parts = signedJws.decodeJwsStrings()
            val signedData = parts.getSignable().encodeToByteArray()
            val signature = parts.signature.decodeFromBase64Url()
            when (keyType) {
                KeyType.Ed25519 -> edKeyPair!!.publicKey.signatureVerifier().verifySignature(signedData, signature)
                KeyType.secp256k1 -> ecKeyPair!!.publicKey.signatureVerifier(SHA256, ECDSA.SignatureFormat.RAW).verifySignature(signedData, signature)
                else -> error("Unexpected key type: $keyType")
            }
            Json.parseToJsonElement(parts.payload.decodeFromBase64Url().decodeToString())
        }

        override suspend fun getPublicKey(): Key = Software(options, edKeyPair, ecKeyPair)
        override suspend fun getPublicKeyRepresentation(): ByteArray = when (keyType) {
            KeyType.Ed25519 -> edKeyPair!!.publicKey.encodeToByteArray(EdDSA.PublicKey.Format.RAW)
            KeyType.secp256k1 -> ecKeyPair!!.publicKey.encodeToByteArray(EC.PublicKey.Format.RAW)
            else -> error("Unexpected key type: $keyType")
        }

        override suspend fun getMeta(): KeyMeta = JwkKeyMeta(getKeyId())
        override suspend fun deleteKey(): Boolean = true
    }
}
