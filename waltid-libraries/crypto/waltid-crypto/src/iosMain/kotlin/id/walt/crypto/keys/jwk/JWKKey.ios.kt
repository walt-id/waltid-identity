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
import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.crypto.utils.JweEncryptionHelper
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
        KeyType.secp256r1, KeyType.Ed25519 -> listOf("d")
        KeyType.RSA -> listOf("d", "p", "q", "dp", "dq", "qi", "oth")
        else -> error("unknown key type")
    }

    actual override val keyType: KeyType
        get() = when {
            _jwkObj["crv"]?.jsonPrimitive?.content == "P-256" -> KeyType.secp256r1
            _jwkObj["kty"]?.jsonPrimitive?.content == "RSA" -> KeyType.RSA
            _jwkObj["crv"]?.jsonPrimitive?.content == "Ed25519" -> KeyType.Ed25519
            else -> error("Unknown key type in jwk $jwk")
        }

    actual override suspend fun getKeyId(): String {
        return _keyId ?: _jwkObj["kid"]?.jsonPrimitive?.content ?: error("Kid not found in $jwk")
    }

    actual override suspend fun getThumbprint(): String {
        val sigJwk = joseCompliantSerializer.decodeFromString<JsonWebKey>(jwk!!)
        return sigJwk.jwkThumbprint
    }

    actual override suspend fun exportJWK(): String = _jwkObj.toString()

    actual override suspend fun exportJWKObject(): JsonObject = _jwkObj

    @OptIn(ExperimentalEncodingApi::class)
    actual override suspend fun exportPEM(): String {
        val cryptoPubKey = joseCompliantSerializer.decodeFromString<JsonWebKey>(jwk!!)
            .toCryptoPublicKey().getOrThrow()
        val derBytes = cryptoPubKey.encodeToTlv().derEncoded
        val base64 = Base64.Default.encode(derBytes).chunked(64).joinToString("\n")
        return "-----BEGIN PUBLIC KEY-----\n$base64\n-----END PUBLIC KEY-----"
    }

    actual override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): ByteArray {
        val kid = getKeyId()
        val signer = IosKeychainProvider.getSignerForKey(kid).getOrThrow()
        val result = signer.sign(plaintext)
        check(result is SignatureResult.Success) { "Signing failed: $result" }
        return result.signature.rawByteArray
    }

    actual override suspend fun signJws(
        plaintext: ByteArray, headers: Map<String, JsonElement>
    ): String {
        val kid = getKeyId()
        val signer = IosKeychainProvider.getSignerForKey(kid).getOrThrow()

        val jwsAlgorithm = when (keyType) {
            KeyType.secp256r1 -> JwsAlgorithm.Signature.EC.ES256
            KeyType.RSA -> JwsAlgorithm.Signature.RSA.RS256
            KeyType.Ed25519 -> error("Ed25519 JWS signing is not yet supported on iOS")
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

        val jws = JwsCompact(
            protectedHeader = header,
            payload = plaintext,
            signer = { data ->
                val signResult = signer.sign(data)
                check(signResult is SignatureResult.Success) { "JWS signing failed: $signResult" }
                signResult.signature.rawByteArray
            }
        )
        return jws.toString()
    }

    actual override suspend fun verifyRaw(
        signed: ByteArray, detachedPlaintext: ByteArray?, customSignatureAlgorithm: String?
    ): Result<ByteArray> = runCatching {
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
        val plaintext = requireNotNull(detachedPlaintext) { "Detached plaintext required for verifyRaw" }
        verifier.verify(SignatureInput(plaintext), signature).getOrThrow()
        plaintext
    }

    actual override suspend fun verifyJws(signedJws: String): Result<JsonElement> = runCatching {
        val parsed = JwsCompact(signedJws)

        val cryptoPubKey = joseCompliantSerializer.decodeFromString<JsonWebKey>(jwk!!)
            .toCryptoPublicKey().getOrThrow()

        val sigAlg = when (keyType) {
            KeyType.secp256r1 -> SignatureAlgorithm.ECDSAwithSHA256
            KeyType.RSA -> SignatureAlgorithm.RSAwithSHA256andPKCS1Padding
            KeyType.Ed25519 -> error("Ed25519 JWS verification is not yet supported on iOS")
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

    actual override suspend fun getPublicKey(): JWKKey = _jwkObj.toMap().filterKeys {
        it !in privateParameters
    }.toJsonObject().toString().let { JWKKey(it) }

    actual override suspend fun getPublicKeyRepresentation(): ByteArray {
        val cryptoPubKey = joseCompliantSerializer.decodeFromString<JsonWebKey>(jwk!!)
            .toCryptoPublicKey().getOrThrow()
        return cryptoPubKey.encodeToTlv().derEncoded
    }

    actual override suspend fun getMeta(): JwkKeyMeta = JwkKeyMeta(getKeyId())

    actual override suspend fun deleteKey(): Boolean {
        return runCatching {
            IosKeychainProvider.deleteSigningKey(getKeyId()).getOrThrow()
        }.isSuccess
    }

    actual override val hasPrivateKey: Boolean
        get() = _jwkObj.toMap().any { it.key in privateParameters }

    actual companion object : JWKKeyCreator() {
        @OptIn(ExperimentalUuidApi::class)
        actual override suspend fun generate(
            type: KeyType, metadata: JwkKeyMeta?
        ): JWKKey {
            val kid = Uuid.random().toString()

            val signer = when (type) {
                KeyType.secp256r1 -> IosKeychainProvider.createSigningKey(kid) {
                    ec { curve = ECCurve.SECP_256_R_1 }
                }.getOrThrow()
                KeyType.RSA -> IosKeychainProvider.createSigningKey(kid) {
                    rsa { }
                }.getOrThrow()
                KeyType.Ed25519 -> error("Ed25519 key generation is not yet supported on iOS")
                else -> error("Key generation not supported for $type on iOS")
            }

            val jwkJson = joseCompliantSerializer.encodeToString(signer.publicKey.toJsonWebKey(kid))
            return JWKKey(jwkJson, kid)
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
