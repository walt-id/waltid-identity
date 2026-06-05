package id.walt.crypto

import at.asitplus.signum.indispensable.CryptoSignature
import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.indispensable.SignatureAlgorithm
import at.asitplus.signum.indispensable.josef.JsonWebKey
import at.asitplus.signum.indispensable.josef.JwsAlgorithm
import at.asitplus.signum.indispensable.josef.JwsCompact
import at.asitplus.signum.indispensable.josef.JwsHeader
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.josef.toJsonWebKey
import at.asitplus.signum.supreme.SignatureResult
import at.asitplus.signum.supreme.dsl.REQUIRED
import at.asitplus.signum.supreme.os.IosKeychainProvider
import at.asitplus.signum.supreme.sign.SignatureInput
import at.asitplus.signum.supreme.sign.verifierFor
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.EdDSA
import dev.whyoleg.cryptography.algorithms.SHA256
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
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

sealed class IosKey : Key() {

    class Options @OptIn(ExperimentalUuidApi::class) constructor(
        val kid: String = Uuid.random().toString(),
        val keyType: KeyType,
        val inSecureElement: Boolean = false
    ) {
        init {
            if (inSecureElement) {
                require(keyType == KeyType.secp256r1) { "kid: $kid, Error: Only KeyType.secp256r1 can be stored in secure element." }
            }
        }
    }


    class Hardware internal constructor(private val options: Options) : IosKey() {

        companion object {
            suspend fun create(options: Options): Hardware {
                when (options.keyType) {
                    KeyType.secp256r1 -> IosKeychainProvider.createSigningKey(options.kid) {
                        ec { curve = ECCurve.SECP_256_R_1 }
                        if (options.inSecureElement) {
                            hardware { backing = REQUIRED }
                        }
                    }.getOrThrow()

                    KeyType.RSA -> IosKeychainProvider.createSigningKey(options.kid) {
                        rsa { }
                    }.getOrThrow()

                    else -> error("Unsupported hardware key type: ${options.keyType}")
                }
                return Hardware(options)
            }

            suspend fun load(options: Options): Hardware {
                IosKeychainProvider.getSignerForKey(options.kid).getOrThrow()
                return Hardware(options)
            }

            suspend fun delete(kid: String) {
                IosKeychainProvider.deleteSigningKey(kid).getOrThrow()
            }
        }

        override val keyType get() = options.keyType
        override val hasPrivateKey = true

        private suspend fun signer() = IosKeychainProvider.getSignerForKey(options.kid).getOrThrow()

        override suspend fun getKeyId(): String = options.kid

        override suspend fun getThumbprint(): String =
            signer().publicKey.toJsonWebKey(options.kid).jwkThumbprint

        override suspend fun exportJWK(): String = exportJWKObject().toString()

        override suspend fun exportJWKObject(): JsonObject {
            val jwk = signer().publicKey.toJsonWebKey(options.kid)
            return Json.parseToJsonElement(joseCompliantSerializer.encodeToString(jwk)).jsonObject
        }

        override suspend fun exportPEM(): String {
            val derBytes = signer().publicKey.encodeToTlv().derEncoded
            val base64 = kotlin.io.encoding.Base64.encode(derBytes).chunked(64).joinToString("\n")
            return "-----BEGIN PUBLIC KEY-----\n$base64\n-----END PUBLIC KEY-----"
        }

        override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): Any {
            val result = signer().sign(plaintext)
            check(result is SignatureResult.Success) { "Signing failed: $result" }
            return result.signature.rawByteArray
        }

        override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
            val signer = signer()
            val jwsAlgorithm = when (options.keyType) {
                KeyType.secp256r1 -> JwsAlgorithm.Signature.EC.ES256
                KeyType.RSA -> JwsAlgorithm.Signature.RSA.PS256
                else -> error("Unsupported key type for JWS: ${options.keyType}")
            }
            val jwkHeader = headers["jwk"]?.let { jwkElement ->
                runCatching { joseCompliantSerializer.decodeFromString<JsonWebKey>(jwkElement.toString()) }.getOrNull()
            }
            val header = JwsHeader(
                algorithm = jwsAlgorithm,
                keyId = headers["kid"]?.let { (it as? JsonPrimitive)?.content },
                type = headers["typ"]?.let { (it as? JsonPrimitive)?.content },
                contentType = headers["cty"]?.let { (it as? JsonPrimitive)?.content },
                jsonWebKey = jwkHeader,
            )
            return JwsCompact(
                protectedHeader = header,
                payload = plaintext,
                signer = { data ->
                    val signResult = signer.sign(data)
                    check(signResult is SignatureResult.Success) { "JWS signing failed: $signResult" }
                    signResult.signature.rawByteArray
                }
            ).toString()
        }

        override suspend fun verifyRaw(
            signed: ByteArray, detachedPlaintext: ByteArray?, customSignatureAlgorithm: String?
        ): Result<ByteArray> = runCatching {
            val plaintext = requireNotNull(detachedPlaintext) { "Detached plaintext required" }
            val cryptoPubKey = signer().publicKey
            val sigAlg = when (options.keyType) {
                KeyType.secp256r1 -> SignatureAlgorithm.ECDSAwithSHA256
                KeyType.RSA -> SignatureAlgorithm.RSAwithSHA256andPSSPadding
                else -> error("Unsupported key type for verification: ${options.keyType}")
            }
            val verifier = sigAlg.verifierFor(cryptoPubKey).getOrThrow()
            val signature = when (options.keyType) {
                KeyType.RSA -> CryptoSignature.RSA(signed)
                else -> CryptoSignature.EC.fromRawBytes(signed)
            }
            verifier.verify(SignatureInput(plaintext), signature).getOrThrow()
            plaintext
        }

        override suspend fun verifyJws(signedJws: String): Result<JsonElement> = runCatching {
            val parsed = JwsCompact(signedJws)
            val cryptoPubKey = signer().publicKey
            val sigAlg = when (options.keyType) {
                KeyType.secp256r1 -> SignatureAlgorithm.ECDSAwithSHA256
                KeyType.RSA -> SignatureAlgorithm.RSAwithSHA256andPSSPadding
                else -> error("Unsupported key type for verification: ${options.keyType}")
            }
            val verifier = sigAlg.verifierFor(cryptoPubKey).getOrThrow()
            val signature = when (options.keyType) {
                KeyType.RSA -> CryptoSignature.RSA(parsed.plainSignature)
                else -> CryptoSignature.EC.fromRawBytes(parsed.plainSignature)
            }
            verifier.verify(SignatureInput(parsed.signatureInput), signature).getOrThrow()
            Json.parseToJsonElement(parsed.plainPayload.decodeToString())
        }

        override suspend fun getPublicKey(): Key = Hardware(options)
        override suspend fun getPublicKeyRepresentation(): ByteArray = signer().publicKey.encodeToTlv().derEncoded
        override suspend fun getMeta(): KeyMeta = JwkKeyMeta(getKeyId())
        override suspend fun deleteKey(): Boolean = runCatching {
            IosKeychainProvider.deleteSigningKey(options.kid).getOrThrow()
        }.isSuccess
    }

    class Software private constructor(
        private val options: Options,
        private val edKeyPair: EdDSA.KeyPair?,
        private val ecKeyPair: ECDSA.KeyPair?,
    ) : IosKey() {

        companion object {
            private val edDsa by lazy { CryptographyProvider.Default.get(EdDSA) }
            private val ecdsa by lazy { CryptographyProvider.Default.get(ECDSA) }

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

            @OptIn(dev.whyoleg.cryptography.CryptographyProviderApi::class)
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

        override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): Any = when (keyType) {
            KeyType.Ed25519 -> edKeyPair!!.privateKey.signatureGenerator().generateSignature(plaintext)
            KeyType.secp256k1 -> ecKeyPair!!.privateKey.signatureGenerator(SHA256, ECDSA.SignatureFormat.RAW).generateSignature(plaintext)
            else -> error("Unexpected key type: $keyType")
        }

        override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
            val (header, payload, signable) = KeyUtils.rawSignaturePayloadForJws(plaintext, headers, keyType)
            val signature = signRaw(signable) as ByteArray
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
