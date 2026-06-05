package id.walt.crypto.keys.jwk

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.CryptoSignature
import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.indispensable.SignatureAlgorithm
import at.asitplus.signum.indispensable.josef.JsonWebKey
import at.asitplus.signum.indispensable.josef.JweAlgorithm
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.signum.indispensable.josef.JweEncrypted.Companion.deserialize
import at.asitplus.signum.indispensable.josef.JwsAlgorithm
import at.asitplus.signum.indispensable.josef.JwsCompact
import at.asitplus.signum.indispensable.josef.JwsHeader
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.josef.toJsonWebKey
import at.asitplus.signum.supreme.SignatureResult
import at.asitplus.signum.supreme.os.IosKeychainProvider
import at.asitplus.signum.supreme.sign.SignatureInput
import at.asitplus.signum.supreme.sign.Signer
import at.asitplus.signum.supreme.sign.verifierFor
import at.asitplus.signum.supreme.symmetric.decrypt
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.EdDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.KeyUtils
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonCanonicalizationUtils
import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.crypto.utils.JweEncryptionHelper
import id.walt.crypto.utils.JwsUtils.decodeJwsStrings
import id.walt.crypto.utils.ShaUtils
import id.walt.crypto.utils.keyFromIntermediate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

actual class JWKKey actual constructor(private val jwk: String?, private val _keyId: String?) : Key() {

    private var _jwkObj: JsonObject =
        Json.parseToJsonElement(requireNotNull(jwk) { "jwk is null" }).jsonObject

    private val privateParameters = when (keyType) {
        KeyType.secp256r1, KeyType.secp256k1, KeyType.Ed25519 -> listOf("d")
        KeyType.RSA -> listOf("d", "p", "q", "dp", "dq", "qi", "oth")
        else -> error("unknown key type")
    }

    actual override val keyType: KeyType
        get() = when {
            _jwkObj["crv"]?.jsonPrimitive?.content == "P-256" -> KeyType.secp256r1
            _jwkObj["crv"]?.jsonPrimitive?.content == "secp256k1" -> KeyType.secp256k1
            _jwkObj["kty"]?.jsonPrimitive?.content == "RSA" -> KeyType.RSA
            _jwkObj["crv"]?.jsonPrimitive?.content == "Ed25519" -> KeyType.Ed25519
            else -> error("Unknown key type in jwk $jwk")
        }

    private val isSoftwareKey get() = keyType == KeyType.Ed25519 || keyType == KeyType.secp256k1

    actual override suspend fun getKeyId(): String {
        return _keyId ?: _jwkObj["kid"]?.jsonPrimitive?.content ?: error("Kid not found in $jwk")
    }

    actual override suspend fun getThumbprint(): String {
        if (isSoftwareKey) {
            val canonical = JsonCanonicalizationUtils.convertToRequiredMembersJsonString(this)
            return ShaUtils.sha256(canonical.encodeToByteArray()).encodeToBase64Url()
        }
        val sigJwk = joseCompliantSerializer.decodeFromString<JsonWebKey>(jwk!!)
        return sigJwk.jwkThumbprint
    }

    actual override suspend fun exportJWK(): String = _jwkObj.toString()

    actual override suspend fun exportJWKObject(): JsonObject = _jwkObj

    @OptIn(ExperimentalEncodingApi::class)
    actual override suspend fun exportPEM(): String {
        if (isSoftwareKey) {
            val pubKey = when (keyType) {
                KeyType.Ed25519 -> SoftwareKeyOps.edDsa.publicKeyDecoder(EdDSA.Curve.Ed25519)
                    .decodeFromByteArray(EdDSA.PublicKey.Format.JWK, jwk!!.encodeToByteArray())
                    .encodeToByteArray(EdDSA.PublicKey.Format.PEM)
                KeyType.secp256k1 -> SoftwareKeyOps.ecdsa.publicKeyDecoder(EC.Curve.secp256k1)
                    .decodeFromByteArray(EC.PublicKey.Format.JWK, jwk!!.encodeToByteArray())
                    .encodeToByteArray(EC.PublicKey.Format.PEM)
                else -> error("Unexpected key type: $keyType")
            }
            return pubKey.decodeToString()
        }
        val cryptoPubKey = joseCompliantSerializer.decodeFromString<JsonWebKey>(jwk!!)
            .toCryptoPublicKey().getOrThrow()
        val derBytes = cryptoPubKey.encodeToTlv().derEncoded
        val base64 = Base64.encode(derBytes).chunked(64).joinToString("\n")
        return "-----BEGIN PUBLIC KEY-----\n$base64\n-----END PUBLIC KEY-----"
    }

    actual override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): ByteArray {
        if (isSoftwareKey) return SoftwareKeyOps.sign(keyType, jwk!!, plaintext)
        val kid = getKeyId()
        val signer = IosKeychainProvider.getSignerForKey(kid).getOrThrow()
        val result = signer.sign(plaintext)
        check(result is SignatureResult.Success) { "Signing failed: $result" }
        return result.signature.rawByteArray
    }

    actual override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
        if (isSoftwareKey) {
            val (header, payload, signable) = KeyUtils.rawSignaturePayloadForJws(plaintext, headers, keyType)
            val signature = signRaw(signable)
            return KeyUtils.signJwsWithRawSignature(signature, header, payload)
        }
        val kid = getKeyId()
        val signer = IosKeychainProvider.getSignerForKey(kid).getOrThrow()
        val jwsAlgorithm = when (keyType) {
            KeyType.secp256r1 -> JwsAlgorithm.Signature.EC.ES256
            KeyType.RSA -> JwsAlgorithm.Signature.RSA.RS256
            else -> error("Unsupported key type: $keyType")
        }
        val jwkHeader = headers["jwk"]?.let { jwkElement ->
            runCatching { joseCompliantSerializer.decodeFromString<JsonWebKey>(jwkElement.toString()) }.getOrNull()
        }
        val header = JwsHeader(
            algorithm = jwsAlgorithm,
            keyId = headers["kid"]?.jsonPrimitive?.content,
            type = headers["typ"]?.jsonPrimitive?.content,
            contentType = headers["cty"]?.jsonPrimitive?.content,
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

    actual override suspend fun verifyRaw(
        signed: ByteArray, detachedPlaintext: ByteArray?, customSignatureAlgorithm: String?
    ): Result<ByteArray> = runCatching {
        val plaintext = requireNotNull(detachedPlaintext) { "Detached plaintext required for verifyRaw" }
        if (isSoftwareKey) {
            SoftwareKeyOps.verify(keyType, jwk!!, plaintext, signed)
            return@runCatching plaintext
        }
        val cryptoPubKey = joseCompliantSerializer.decodeFromString<JsonWebKey>(jwk!!)
            .toCryptoPublicKey().getOrThrow()
        val sigAlg = when (keyType) {
            KeyType.secp256r1 -> SignatureAlgorithm.ECDSAwithSHA256
            KeyType.RSA -> SignatureAlgorithm.RSAwithSHA256andPKCS1Padding
            else -> error("Unsupported key type for verification: $keyType")
        }
        val verifier = sigAlg.verifierFor(cryptoPubKey).getOrThrow()
        val signature = when (keyType) {
            KeyType.RSA -> CryptoSignature.RSA(signed)
            else -> CryptoSignature.EC.fromRawBytes(signed)
        }
        verifier.verify(SignatureInput(plaintext), signature).getOrThrow()
        plaintext
    }

    actual override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        if (isSoftwareKey) return runCatching {
            val parts = signedJws.decodeJwsStrings()
            val signedData = parts.getSignable().encodeToByteArray()
            val signature = parts.signature.decodeFromBase64Url()
            SoftwareKeyOps.verify(keyType, jwk!!, signedData, signature)
            Json.parseToJsonElement(parts.payload.decodeFromBase64Url().decodeToString())
        }
        return runCatching {
            val parsed = JwsCompact(signedJws)
            val cryptoPubKey = joseCompliantSerializer.decodeFromString<JsonWebKey>(jwk!!)
                .toCryptoPublicKey().getOrThrow()
            val sigAlg = when (keyType) {
                KeyType.secp256r1 -> SignatureAlgorithm.ECDSAwithSHA256
                KeyType.RSA -> SignatureAlgorithm.RSAwithSHA256andPKCS1Padding
                else -> error("Unsupported key type for verification: $keyType")
            }
            val verifier = sigAlg.verifierFor(cryptoPubKey).getOrThrow()
            val signature = when (keyType) {
                KeyType.RSA -> CryptoSignature.RSA(parsed.plainSignature)
                else -> CryptoSignature.EC.fromRawBytes(parsed.plainSignature)
            }
            verifier.verify(SignatureInput(parsed.signatureInput), signature).getOrThrow()
            Json.parseToJsonElement(parsed.plainPayload.decodeToString())
        }
    }

    actual override suspend fun getPublicKey(): JWKKey = _jwkObj.toMap().filterKeys {
        it !in privateParameters
    }.toJsonObject().toString().let { JWKKey(it) }

    actual override suspend fun getPublicKeyRepresentation(): ByteArray {
        if (isSoftwareKey) {
            return when (keyType) {
                KeyType.Ed25519 -> SoftwareKeyOps.edDsa.publicKeyDecoder(EdDSA.Curve.Ed25519)
                    .decodeFromByteArray(EdDSA.PublicKey.Format.JWK, jwk!!.encodeToByteArray())
                    .encodeToByteArray(EdDSA.PublicKey.Format.RAW)
                KeyType.secp256k1 -> SoftwareKeyOps.ecdsa.publicKeyDecoder(EC.Curve.secp256k1)
                    .decodeFromByteArray(EC.PublicKey.Format.JWK, jwk!!.encodeToByteArray())
                    .encodeToByteArray(EC.PublicKey.Format.RAW)
                else -> error("Unexpected key type: $keyType")
            }
        }
        val cryptoPubKey = joseCompliantSerializer.decodeFromString<JsonWebKey>(jwk!!)
            .toCryptoPublicKey().getOrThrow()
        return cryptoPubKey.encodeToTlv().derEncoded
    }

    actual override suspend fun getMeta(): JwkKeyMeta = JwkKeyMeta(getKeyId())

    actual override suspend fun deleteKey(): Boolean {
        if (isSoftwareKey) return true
        return runCatching {
            IosKeychainProvider.deleteSigningKey(getKeyId()).getOrThrow()
        }.isSuccess
    }

    actual override val hasPrivateKey: Boolean
        get() = _jwkObj.toMap().any { it.key in privateParameters }

    actual companion object : JWKKeyCreator() {
        @OptIn(ExperimentalUuidApi::class)
        actual override suspend fun generate(type: KeyType, metadata: JwkKeyMeta?): JWKKey {
            val kid = metadata?.keyId ?: Uuid.random().toString()
            return when (type) {
                KeyType.Ed25519 -> {
                    val keyPair = SoftwareKeyOps.edDsa.keyPairGenerator(EdDSA.Curve.Ed25519).generateKey()
                    val jwkBytes = keyPair.privateKey.encodeToByteArray(EdDSA.PrivateKey.Format.JWK)
                    JWKKey(jwkBytes.decodeToString(), kid)
                }
                KeyType.secp256k1 -> {
                    val keyPair = SoftwareKeyOps.ecdsa.keyPairGenerator(EC.Curve.secp256k1).generateKey()
                    val jwkBytes = keyPair.privateKey.encodeToByteArray(EC.PrivateKey.Format.JWK)
                    JWKKey(jwkBytes.decodeToString(), kid)
                }
                KeyType.secp256r1 -> {
                    val signer = IosKeychainProvider.createSigningKey(kid) {
                        ec { curve = ECCurve.SECP_256_R_1 }
                    }.getOrThrow()
                    val jwkJson = joseCompliantSerializer.encodeToString(signer.publicKey.toJsonWebKey(kid))
                    JWKKey(jwkJson, kid)
                }
                KeyType.RSA -> {
                    val signer = IosKeychainProvider.createSigningKey(kid) {
                        rsa { }
                    }.getOrThrow()
                    val jwkJson = joseCompliantSerializer.encodeToString(signer.publicKey.toJsonWebKey(kid))
                    JWKKey(jwkJson, kid)
                }
                else -> error("Key generation not supported for $type on iOS")
            }
        }

        @OptIn(ExperimentalEncodingApi::class)
        actual override suspend fun importRawPublicKey(
            type: KeyType, rawPublicKey: ByteArray, metadata: JwkKeyMeta?
        ): Key {
            val cryptoPubKey = when (type) {
                KeyType.secp256r1 -> CryptoPublicKey.EC.fromAnsiX963Bytes(ECCurve.SECP_256_R_1, rawPublicKey)
                else -> CryptoPublicKey.decodeFromDer(rawPublicKey)
            }
            val jwkJson = joseCompliantSerializer.encodeToString(cryptoPubKey.toJsonWebKey(metadata?.keyId))
            return JWKKey(jwkJson, metadata?.keyId)
        }

        actual override suspend fun importJWK(jwk: String): Result<JWKKey> {
            return Result.success(JWKKey(jwk))
        }

        @OptIn(ExperimentalEncodingApi::class)
        actual override suspend fun importPEM(pem: String): Result<JWKKey> = runCatching {
            val derBytes = pem.lines()
                .filter { !it.startsWith("-----") }
                .joinToString("")
                .let { Base64.decode(it) }

            val cryptoPubKey = if (pem.contains("BEGIN CERTIFICATE")) {
                X509Certificate.decodeFromDer(derBytes).decodedPublicKey.getOrThrow()
            } else {
                CryptoPublicKey.decodeFromDer(derBytes)
            }
            val jwkJson = joseCompliantSerializer.encodeToString(cryptoPubKey.toJsonWebKey())
            JWKKey(jwkJson)
        }
    }

    override fun hashCode(): Int {
        var result = _keyId?.hashCode() ?: 0
        val jsonHash = _jwkObj.toString().hashCode()
        result = 31 * result + jsonHash
        return result
    }

    actual suspend fun decryptJwe(jweString: String): ByteArray {
        check(hasPrivateKey) { "Private key required for decryption." }
        check(keyType == KeyType.secp256r1) { "ECDH-ES decryption only supported for EC P-256 keys." }

        val jwe = deserialize(jweString).getOrThrow()
        val header = jwe.header
        require(header.algorithm == JweAlgorithm.ECDH_ES)

        val epk = header.ephemeralKeyPair?.toCryptoPublicKey()?.getOrThrow() as CryptoPublicKey.EC
        val signer = IosKeychainProvider.getSignerForKey(getKeyId()).getOrThrow()

        val z = (signer as Signer.ECDSA).keyAgreement(epk).getOrThrow()

        val encryption = header.encryption!!
        val keyLenBits = encryption.combinedEncryptionKeyLength.bits.toInt()
        val cekBytes = JweEncryptionHelper.concatKdf(z, keyLenBits, encryption.identifier)

        val key = keyFromIntermediate(encryption.algorithm, cekBytes)

        val aad = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
            .encode(jwe.headerAsParsed).encodeToByteArray()
        return key.decrypt(jwe.iv, jwe.ciphertext, jwe.authTag, aad).getOrThrow()
    }

    actual suspend fun encryptJwe(plaintext: ByteArray, encAlg: String): String {
        check(keyType == KeyType.secp256r1) {
            "ECDH-ES is currently only supported for EC P-256 keys. Current key type: $keyType"
        }
        val recipientKey = joseCompliantSerializer.decodeFromString<JsonWebKey>(jwk!!)
            .toCryptoPublicKey().getOrThrow() as CryptoPublicKey.EC
        return JweEncryptionHelper.encryptEcdhEs(
            plaintext = plaintext,
            recipientPublicKey = recipientKey,
            encAlg = encAlg,
            keyId = _jwkObj["kid"]?.jsonPrimitive?.content
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        if (!super.equals(other)) return false

        other as JWKKey

        if (jwk != other.jwk) return false
        if (_keyId != other._keyId) return false
        if (_jwkObj != other._jwkObj) return false
        if (privateParameters != other.privateParameters) return false
        if (hasPrivateKey != other.hasPrivateKey) return false
        if (keyType != other.keyType) return false

        return true
    }
}

private object SoftwareKeyOps {
    val edDsa by lazy { CryptographyProvider.Default.get(EdDSA) }
    val ecdsa by lazy { CryptographyProvider.Default.get(ECDSA) }

    suspend fun sign(keyType: KeyType, jwk: String, data: ByteArray): ByteArray {
        val jwkBytes = jwk.encodeToByteArray()
        return when (keyType) {
            KeyType.Ed25519 -> edDsa.privateKeyDecoder(EdDSA.Curve.Ed25519)
                .decodeFromByteArray(EdDSA.PrivateKey.Format.JWK, jwkBytes)
                .signatureGenerator()
                .generateSignature(data)
            KeyType.secp256k1 -> ecdsa.privateKeyDecoder(EC.Curve.secp256k1)
                .decodeFromByteArray(EC.PrivateKey.Format.JWK, jwkBytes)
                .signatureGenerator(SHA256, ECDSA.SignatureFormat.RAW)
                .generateSignature(data)
            else -> error("Unexpected software key type: $keyType")
        }
    }

    suspend fun verify(keyType: KeyType, jwk: String, data: ByteArray, signature: ByteArray) {
        val jwkBytes = jwk.encodeToByteArray()
        when (keyType) {
            KeyType.Ed25519 -> edDsa.publicKeyDecoder(EdDSA.Curve.Ed25519)
                .decodeFromByteArray(EdDSA.PublicKey.Format.JWK, jwkBytes)
                .signatureVerifier()
                .verifySignature(data, signature)
            KeyType.secp256k1 -> ecdsa.publicKeyDecoder(EC.Curve.secp256k1)
                .decodeFromByteArray(EC.PublicKey.Format.JWK, jwkBytes)
                .signatureVerifier(SHA256, ECDSA.SignatureFormat.RAW)
                .verifySignature(data, signature)
            else -> error("Unexpected software key type: $keyType")
        }
    }
}
