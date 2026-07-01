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
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.CryptographyProviderApi
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.EdDSA
import id.walt.crypto.MobileSoftwareKey
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

@Serializable
@SerialName("jwk")
actual class JWKKey actual constructor(
    @Serializable(with = JWKKeyJsonFieldSerializer::class)
    var jwk: String?,
    val _keyId: String?,
) : Key() {

    private var _jwkObj: JsonObject =
        Json.parseToJsonElement(requireNotNull(jwk) { "jwk is null" }).jsonObject

    private val privateParameters = when (keyType) {
        KeyType.secp256r1, KeyType.secp256k1, KeyType.secp384r1, KeyType.secp521r1, KeyType.Ed25519 -> listOf("d")
        KeyType.RSA -> listOf("d", "p", "q", "dp", "dq", "qi", "oth")
        else -> error("unknown key type")
    }

    actual override val keyType: KeyType
        get() = when {
            _jwkObj["crv"]?.jsonPrimitive?.content == "P-256" -> KeyType.secp256r1
            _jwkObj["crv"]?.jsonPrimitive?.content == "P-384" -> KeyType.secp384r1
            _jwkObj["crv"]?.jsonPrimitive?.content == "P-521" -> KeyType.secp521r1
            _jwkObj["crv"]?.jsonPrimitive?.content == "secp256k1" -> KeyType.secp256k1
            _jwkObj["crv"]?.jsonPrimitive?.content == "P-256K" -> KeyType.secp256k1
            _jwkObj["kty"]?.jsonPrimitive?.content == "RSA" -> KeyType.RSA
            _jwkObj["crv"]?.jsonPrimitive?.content == "Ed25519" -> KeyType.Ed25519
            else -> error("Unknown key type in jwk $jwk")
        }

    private val isSoftwareKey get() = keyType == KeyType.Ed25519 || keyType == KeyType.secp256k1

    private suspend fun softwareKey(): MobileSoftwareKey {
        val kid = _keyId ?: _jwkObj["kid"]?.jsonPrimitive?.content ?: ""
        val normalizedJwk = normalizeSoftwareJwk().toString().encodeToByteArray()
        return MobileSoftwareKey.load(CryptographyProvider.Default, kid, keyType, normalizedJwk)
    }

    private fun normalizeSoftwareJwk(): JsonObject =
        if (_jwkObj["crv"]?.jsonPrimitive?.content == "P-256K") {
            JsonObject(_jwkObj.toMutableMap().apply {
                this["crv"] = JsonPrimitive("secp256k1")
            })
        } else {
            _jwkObj
        }

    actual override suspend fun getKeyId(): String {
        return _keyId ?: _jwkObj["kid"]?.jsonPrimitive?.content ?: error("Kid not found in $jwk")
    }

    actual override suspend fun getThumbprint(): String {
        if (isSoftwareKey) {
            return softwareKey().getThumbprint()
        }
        val sigJwk = joseCompliantSerializer.decodeFromString<JsonWebKey>(jwk!!)
        return sigJwk.jwkThumbprint
    }

    actual override suspend fun exportJWK(): String = _jwkObj.toString()

    actual override suspend fun exportJWKObject(): JsonObject = _jwkObj

    actual override suspend fun exportPEM(): String {
        if (isSoftwareKey) {
            return softwareKey().exportPEM()
        }
        val cryptoPubKey = joseCompliantSerializer.decodeFromString<JsonWebKey>(jwk!!)
            .toCryptoPublicKey().getOrThrow()
        val derBytes = cryptoPubKey.encodeToTlv().derEncoded
        val base64 = Base64.encode(derBytes).chunked(64).joinToString("\n")
        return "-----BEGIN PUBLIC KEY-----\n$base64\n-----END PUBLIC KEY-----"
    }

    actual override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): ByteArray {
        if (isSoftwareKey) return softwareKey().signRawBytes(plaintext, customSignatureAlgorithm)
        val kid = getKeyId()
        val signer = getKeychainSigner(kid)
        val result = signer.sign(plaintext)
        check(result is SignatureResult.Success) { "Signing failed: $result" }
        return result.signature.rawByteArray
    }

    actual override suspend fun signJws(plaintext: ByteArray, headers: Map<String, JsonElement>): String {
        if (isSoftwareKey) {
            return softwareKey().signJws(plaintext, headers)
        }
        val kid = getKeyId()
        val signer = getKeychainSigner(kid)
        return signJwsWithPlatformSigner(keyType, plaintext, headers) { data -> signer.sign(data) }
    }

    actual override suspend fun verifyRaw(
        signed: ByteArray, detachedPlaintext: ByteArray?, customSignatureAlgorithm: String?
    ): Result<ByteArray> {
        if (isSoftwareKey) return softwareKey().verifyRaw(signed, detachedPlaintext, customSignatureAlgorithm)
        return runCatching {
            val cryptoPubKey = joseCompliantSerializer.decodeFromString<JsonWebKey>(jwk!!)
                .toCryptoPublicKey().getOrThrow()
            verifyRawWithPlatformSigner(keyType, cryptoPubKey, signed, detachedPlaintext).getOrThrow()
        }
    }

    actual override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        if (isSoftwareKey) return softwareKey().verifyJws(signedJws)
        return runCatching {
            val cryptoPubKey = joseCompliantSerializer.decodeFromString<JsonWebKey>(jwk!!)
                .toCryptoPublicKey().getOrThrow()
            verifyJwsWithPlatformSigner(keyType, cryptoPubKey, signedJws).getOrThrow()
        }
    }

    actual override suspend fun getPublicKey(): JWKKey = _jwkObj.toMap().filterKeys {
        it !in privateParameters
    }.toJsonObject().toString().let { JWKKey(it, _keyId) }

    actual override suspend fun getPublicKeyRepresentation(): ByteArray {
        if (isSoftwareKey) {
            return softwareKey().getPublicKeyRepresentation()
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
        actual override suspend fun generate(type: KeyType, metadata: JwkKeyMeta?): JWKKey {
            val kid = metadata?.keyId ?: Uuid.random().toString()
            return when (type) {
                KeyType.Ed25519 -> {
                    val jwkBytes = MobileSoftwareKey.create(CryptographyProvider.Default, kid, type)
                        .exportPrivateKeyMaterial()
                    JWKKey(jwkBytes.decodeToString(), kid)
                }
                KeyType.secp256k1 -> {
                    val jwkBytes = MobileSoftwareKey.create(CryptographyProvider.Default, kid, type)
                        .exportPrivateKeyMaterial()
                    JWKKey(jwkBytes.decodeToString(), kid)
                }
                KeyType.secp256r1, KeyType.secp384r1, KeyType.secp521r1 -> {
                    val signer = IosKeychainProvider.createSigningKey(kid) {
                        ec { curve = requireNotNull(type.toPlatformKeyStoreCurve()) }
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

        actual override suspend fun importRawPublicKey(
            type: KeyType, rawPublicKey: ByteArray, metadata: JwkKeyMeta?
        ): Key {
            val cryptoPubKey = when (type) {
                KeyType.Ed25519, KeyType.secp256k1 -> return importSoftwareRawPublicKey(type, rawPublicKey, metadata)
                KeyType.secp256r1 -> CryptoPublicKey.EC.fromAnsiX963Bytes(ECCurve.SECP_256_R_1, rawPublicKey)
                KeyType.secp384r1 -> CryptoPublicKey.EC.fromAnsiX963Bytes(ECCurve.SECP_384_R_1, rawPublicKey)
                KeyType.secp521r1 -> CryptoPublicKey.EC.fromAnsiX963Bytes(ECCurve.SECP_521_R_1, rawPublicKey)
                else -> CryptoPublicKey.decodeFromDer(rawPublicKey)
            }
            val jwkJson = joseCompliantSerializer.encodeToString(cryptoPubKey.toJsonWebKey(metadata?.keyId))
            return JWKKey(jwkJson, metadata?.keyId)
        }

        @OptIn(CryptographyProviderApi::class)
        private suspend fun importSoftwareRawPublicKey(
            type: KeyType,
            rawPublicKey: ByteArray,
            metadata: JwkKeyMeta?,
        ): JWKKey {
            val jwkBytes = when (type) {
                KeyType.Ed25519 -> CryptographyProvider.Default.get(EdDSA)
                    .publicKeyDecoder(EdDSA.Curve.Ed25519)
                    .decodeFromByteArray(EdDSA.PublicKey.Format.RAW, rawPublicKey)
                    .encodeToByteArray(EdDSA.PublicKey.Format.JWK)
                KeyType.secp256k1 -> CryptographyProvider.Default.get(ECDSA)
                    .publicKeyDecoder(EC.Curve.secp256k1)
                    .decodeFromByteArray(EC.PublicKey.Format.RAW, rawPublicKey)
                    .encodeToByteArray(EC.PublicKey.Format.JWK)
                else -> error("Unsupported software key type: $type")
            }
            val jwk = Json.parseToJsonElement(jwkBytes.decodeToString()).jsonObject.toMutableMap().apply {
                metadata?.keyId?.let { this["kid"] = JsonPrimitive(it) } ?: remove("kid")
            }
            return JWKKey(JsonObject(jwk).toString(), metadata?.keyId)
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
        val signer = getKeychainSigner(getKeyId())

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

private suspend fun JWKKey.getKeychainSigner(kid: String): Signer =
    IosKeychainProvider.getSignerForKey(kid).getOrElse { cause ->
        error(
            "Imported private JWK signing/decryption is not supported for $keyType on iOS. " +
                "Use generated Keychain-backed keys, or Ed25519/secp256k1 software keys. Cause: ${cause.message}"
        )
    }
