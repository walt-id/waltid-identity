package id.walt.crypto.keys.jwk

import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.target.ios.keys.Ed25519
import id.walt.target.ios.keys.P256
import id.walt.target.ios.keys.RSA
import id.walt.target.ios.keys.toNSData
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecCertificateCopyKey
import platform.Security.SecCertificateCreateWithData
import platform.Security.SecKeyCopyExternalRepresentation

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


    actual override suspend fun getThumbprint(): String = when (keyType) {
        KeyType.secp256r1 -> P256.PublicKey.fromJwk(jwk!!)
        KeyType.Ed25519 -> Ed25519.PublicKey.fromJwk(jwk!!)
        KeyType.RSA -> RSA.PublicKey.fromJwk(jwk!!)
        else -> error("Not implemented for $keyType")
    }.thumbprint()

    actual override suspend fun exportJWK(): String = _jwkObj.toString()


    actual override suspend fun exportJWKObject(): JsonObject = _jwkObj

    actual override suspend fun exportPEM(): String = when (keyType) {
        KeyType.secp256r1 -> P256.PublicKey.fromJwk(jwk!!)
        KeyType.Ed25519 -> Ed25519.PublicKey.fromJwk(jwk!!)
        KeyType.RSA -> RSA.PublicKey.fromJwk(jwk!!)
        else -> error("Not implemented for $keyType")
    }.pem()

    /**
     * Signs as a JWS: Signs a message using this private key (with the algorithm this key is based on)
     * @exception IllegalArgumentException when this is not a private key
     * @param plaintext data to be signed
     * @return signed (JWS)
     */
    actual override suspend fun signRaw(plaintext: ByteArray, customSignatureAlgorithm: String?): ByteArray {
        val kid = getKeyId()
        return when (keyType) {
            KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(kid, inSecureEnclave = false).signRaw(plaintext)
            KeyType.Ed25519 -> Ed25519.PrivateKey.loadFromKeychain(kid).signRaw(plaintext)
            else -> error("signRaw not implemented for $keyType on iOS") // TODO: RSA signing via RSA.PrivateKey.loadFromKeychain
        }
    }

    actual override suspend fun signJws(
        plaintext: ByteArray, headers: Map<String, JsonElement>
    ): String {
        val kid = getKeyId()
        return when (keyType) {
            KeyType.secp256r1 -> P256.PrivateKey.loadFromKeychain(kid, inSecureEnclave = false).signJws(plaintext, headers)
            KeyType.Ed25519 -> Ed25519.PrivateKey.loadFromKeychain(kid).signJws(plaintext, headers)
            else -> error("signJws not implemented for $keyType on iOS") // TODO: RSA signing via RSA.PrivateKey.loadFromKeychain
        }
    }

    /**
     * Verifies JWS: Verifies a signed message using this public key
     * @param signed signed
     * @return Result wrapping the plaintext; Result failure when the signature fails
     */
    actual override suspend fun verifyRaw(
        signed: ByteArray, detachedPlaintext: ByteArray?, customSignatureAlgorithm: String?
    ): Result<ByteArray> = when (keyType) {
        KeyType.secp256r1 -> P256.PublicKey.fromJwk(jwk!!)
        KeyType.Ed25519 -> Ed25519.PublicKey.fromJwk(jwk!!)
        KeyType.RSA -> RSA.PublicKey.fromJwk(jwk!!)
        else -> error("Not implemented for $keyType")
    }.verifyRaw(signed, detachedPlaintext!!) // TODO: handle null detachedPlaintext (crashes if called without it)

    actual override suspend fun verifyJws(signedJws: String): Result<JsonElement> = when (keyType) {
        KeyType.secp256r1 -> P256.PublicKey.fromJwk(jwk!!)
        KeyType.Ed25519 -> Ed25519.PublicKey.fromJwk(jwk!!)
        KeyType.RSA -> RSA.PublicKey.fromJwk(jwk!!)
        else -> error("Not implemented for $keyType")
    }.verifyJws(signedJws)

    actual override suspend fun getPublicKey(): JWKKey = _jwkObj.toMap().filterKeys {
        it !in privateParameters
    }.toJsonObject().toString().let { JWKKey(it) }


    actual override suspend fun getPublicKeyRepresentation(): ByteArray = when (keyType) {
        KeyType.secp256r1 -> P256.PublicKey.fromJwk(jwk!!)
        KeyType.Ed25519 -> Ed25519.PublicKey.fromJwk(jwk!!)
        KeyType.RSA -> RSA.PublicKey.fromJwk(jwk!!)
        else -> error("Not implemented for $keyType")
    }.externalRepresentation()

    actual override suspend fun getMeta(): JwkKeyMeta {
        TODO("Not yet implemented")
    }

    actual override suspend fun deleteKey(): Boolean {
        TODO("Not yet implemented")
    }

    actual override val hasPrivateKey: Boolean
        get() = _jwkObj.toMap().any { it.key in privateParameters }

    actual companion object : JWKKeyCreator() {
        @OptIn(ExperimentalUuidApi::class)
        actual override suspend fun generate(
            type: KeyType, metadata: JwkKeyMeta?
        ): JWKKey {
            val kid = Uuid.random().toString()
            val jwkJson = when (type) {
                KeyType.secp256r1 -> P256.PrivateKey.createInKeychain(kid, inSecureEnclave = false).jwk()
                KeyType.Ed25519 -> Ed25519.PrivateKey.createInKeychain(kid).jwk()
                KeyType.RSA -> RSA.PrivateKey.createInKeychain(kid, size = 2048u).jwk()
                else -> error("Key generation not supported for $type on iOS")
            }
            return JWKKey(jwkJson.toString(), kid)
        }

        actual override suspend fun importRawPublicKey(
            type: KeyType, rawPublicKey: ByteArray, metadata: JwkKeyMeta?
        ): Key {
            TODO("Not yet implemented")
        }

        actual override suspend fun importJWK(jwk: String): Result<JWKKey> {
            return Result.success(JWKKey(jwk))
        }

        @OptIn(ExperimentalEncodingApi::class, ExperimentalForeignApi::class)
        actual override suspend fun importPEM(pem: String): Result<JWKKey> = runCatching {
            val derBytes = pem.lines()
                .filter { !it.startsWith("-----") }
                .joinToString("")
                .let { Base64.decode(it) }

            val nsData = derBytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = derBytes.size.toULong())
            }

            // TODO: cfData, certificate, and publicKey are CF objects following the Create/Copy Rule
            // and should be released with CFRelease to avoid memory leaks in long-running processes.
            @Suppress("UNCHECKED_CAST")
            val cfData = CFBridgingRetain(nsData) as platform.CoreFoundation.CFDataRef

            val certificate = SecCertificateCreateWithData(null, cfData)
                ?: error("Failed to create SecCertificate from PEM data")

            val publicKey = SecCertificateCopyKey(certificate)
                ?: error("Failed to extract public key from certificate")

            val keyData = SecKeyCopyExternalRepresentation(publicKey, null)
                ?: error("Failed to get external representation of public key")

            val keyNsData = CFBridgingRelease(keyData) as NSData
            val keyBytes = ByteArray(keyNsData.length.toInt()).also { bytes ->
                bytes.usePinned { pinned ->
                    platform.posix.memcpy(pinned.addressOf(0), keyNsData.bytes, keyNsData.length)
                }
            }

            val jwkJson = when {
                keyBytes.size == 65 && keyBytes[0] == 0x04.toByte() -> {
                    val x = Base64.UrlSafe.encode(keyBytes.sliceArray(1..32)).trimEnd('=')
                    val y = Base64.UrlSafe.encode(keyBytes.sliceArray(33..64)).trimEnd('=')
                    """{"kty":"EC","crv":"P-256","x":"$x","y":"$y"}"""
                }
                // TODO: RSA parsing is incorrect — SecKeyCopyExternalRepresentation returns PKCS#1 DER
            // (SEQUENCE { INTEGER n, INTEGER e }), not raw n bytes. Needs ASN.1 parsing.
            keyBytes.size > 65 -> {
                    val b64 = Base64.UrlSafe.encode(keyBytes).trimEnd('=')
                    """{"kty":"RSA","n":"$b64","e":"AQAB"}"""
                }
                else -> error("Unsupported key format from certificate (${keyBytes.size} bytes)")
            }

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
        TODO("Not yet implemented")
    }

    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun encryptJwe(plaintext: ByteArray, encAlg: String): String {
        val recipientJwk = _jwkObj.toString()
        val kid = _jwkObj["kid"]?.jsonPrimitive?.content

        val result = id.walt.platform.utils.ios.JWE_Operations.encryptWithPlaintext(
            plaintext.toNSData(),
            recipientJwk,
            encAlg,
            kid
        )

        check(result.success()) {
            result.errorMessage() ?: "JWE encryption failed"
        }

        return result.data() ?: error("JWE encryption returned no data")
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
