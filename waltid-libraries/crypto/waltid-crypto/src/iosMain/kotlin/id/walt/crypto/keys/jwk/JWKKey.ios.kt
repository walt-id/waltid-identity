package id.walt.crypto.keys.jwk

import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.target.ios.keys.Ed25519
import id.walt.target.ios.keys.P256
import id.walt.target.ios.keys.RSA
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

actual class JWKKey actual constructor(private val jwk: String?) : Key() {

    private var _jwkObj: JsonObject =
        Json.parseToJsonElement(requireNotNull(jwk) { "jws is null" }).jsonObject

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
        return _jwkObj["kid"]?.jsonPrimitive?.content ?: error("Kid not found in $jwk")
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
    actual override suspend fun signRaw(plaintext: ByteArray): ByteArray {
        error("Not implemented")
    }

    actual override suspend fun signJws(
        plaintext: ByteArray, headers: Map<String, String>
    ): String {
        error("Not implemented")
    }

    /**
     * Verifies JWS: Verifies a signed message using this public key
     * @param signed signed
     * @return Result wrapping the plaintext; Result failure when the signature fails
     */
    actual override suspend fun verifyRaw(
        signed: ByteArray, detachedPlaintext: ByteArray?
    ): Result<ByteArray> = when (keyType) {
        KeyType.secp256r1 -> P256.PublicKey.fromJwk(jwk!!)
        KeyType.Ed25519 -> Ed25519.PublicKey.fromJwk(jwk!!)
        KeyType.RSA -> RSA.PublicKey.fromJwk(jwk!!)
        else -> error("Not implemented for $keyType")
    }.verifyRaw(signed, detachedPlaintext!!)

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

    actual override val hasPrivateKey: Boolean
        get() = _jwkObj.toMap().any { it.key in privateParameters }

    actual companion object : JWKKeyCreator {
        actual override suspend fun generate(
            type: KeyType, metadata: JwkKeyMeta?
        ): JWKKey {
            TODO("Not yet implemented")
        }

        actual override suspend fun importRawPublicKey(
            type: KeyType, rawPublicKey: ByteArray, metadata: JwkKeyMeta?
        ): Key {
            TODO("Not yet implemented")
        }

        actual override suspend fun importJWK(jwk: String): Result<JWKKey> {
            return Result.success(JWKKey(jwk))
        }

        actual override suspend fun importPEM(pem: String): Result<JWKKey> {
            TODO("Not yet implemented")
        }

    }
}