package id.walt.crypto.keys.jwk

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.ECCurve
import at.asitplus.signum.indispensable.josef.JsonWebKey
import at.asitplus.signum.indispensable.josef.JweAlgorithm
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.signum.indispensable.josef.JweEncrypted.Companion.deserialize
import at.asitplus.signum.indispensable.josef.io.joseCompliantSerializer
import at.asitplus.signum.indispensable.josef.toJsonWebKey
import at.asitplus.signum.supreme.SignatureResult
import at.asitplus.signum.supreme.os.IosKeychainProvider
import at.asitplus.signum.supreme.sign.Signer
import at.asitplus.signum.supreme.symmetric.decrypt
import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.signJwsWithPlatformSigner
import id.walt.crypto.toPlatformKeyStoreCurve
import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.crypto.utils.JweEncryptionHelper
import id.walt.crypto.utils.keyFromIntermediate
import id.walt.crypto.verifyJwsWithPlatformSigner
import id.walt.crypto.verifyRawWithPlatformSigner
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

actual class JWKKey actual constructor(private val jwk: String?, private val _keyId: String?) : Key() {

    private var _jwkObj: JsonObject =
        Json.parseToJsonElement(requireNotNull(jwk) { "jwk is null" }).jsonObject

    private val privateParameters = when (keyType) {
        KeyType.secp256r1, KeyType.secp384r1, KeyType.secp521r1, KeyType.Ed25519 -> listOf("d")
        KeyType.RSA -> listOf("d", "p", "q", "dp", "dq", "qi", "oth")
        else -> error("unknown key type")
    }

    actual override val keyType: KeyType
        get() = when {
            _jwkObj["crv"]?.jsonPrimitive?.content == "P-256" -> KeyType.secp256r1
            _jwkObj["crv"]?.jsonPrimitive?.content == "P-384" -> KeyType.secp384r1
            _jwkObj["crv"]?.jsonPrimitive?.content == "P-521" -> KeyType.secp521r1
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
        return signJwsWithPlatformSigner(keyType, plaintext, headers) { data -> signer.sign(data) }
    }

    actual override suspend fun verifyRaw(
        signed: ByteArray, detachedPlaintext: ByteArray?, customSignatureAlgorithm: String?
    ): Result<ByteArray> = runCatching {
        val cryptoPubKey = joseCompliantSerializer.decodeFromString<JsonWebKey>(jwk!!)
            .toCryptoPublicKey().getOrThrow()
        verifyRawWithPlatformSigner(keyType, cryptoPubKey, signed, detachedPlaintext).getOrThrow()
    }

    actual override suspend fun verifyJws(signedJws: String): Result<JsonElement> = runCatching {
        val cryptoPubKey = joseCompliantSerializer.decodeFromString<JsonWebKey>(jwk!!)
            .toCryptoPublicKey().getOrThrow()
        verifyJwsWithPlatformSigner(keyType, cryptoPubKey, signedJws).getOrThrow()
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
        actual override suspend fun generate(
            type: KeyType, metadata: JwkKeyMeta?
        ): JWKKey {
            val kid = Uuid.random().toString()

            val signer = when (type) {
                KeyType.secp256r1 -> IosKeychainProvider.createSigningKey(kid) {
                    ec { curve = requireNotNull(type.toPlatformKeyStoreCurve()) }
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
