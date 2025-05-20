package id.walt.crypto.keys.jwk

import id.walt.crypto.keys.JwkKeyMeta
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import kotlinx.serialization.json.*

actual class JWKKey actual constructor(
    private val jwk: String?,
    private val _keyId: String?
) : Key() {
    private var _jwkObj: JsonObject? = null

    override suspend fun init() { // Removed 'actual' modifier since it's not in expect
        if (_jwkObj == null) {
            _jwkObj = jwk?.let { Json.parseToJsonElement(it).jsonObject }
        }
    }

    private val privateParameters = when (keyType) {
        KeyType.secp256r1, KeyType.Ed25519 -> listOf("d")
        KeyType.RSA -> listOf("d", "p", "q", "dp", "dq", "qi", "oth")
        else -> error("unknown key type")
    }

    actual override val keyType: KeyType
        get() = when {
            _jwkObj?.get("crv")?.jsonPrimitive?.content == "P-256" -> KeyType.secp256r1
            _jwkObj?.get("kty")?.jsonPrimitive?.content == "RSA" -> KeyType.RSA
            _jwkObj?.get("crv")?.jsonPrimitive?.content == "Ed25519" -> KeyType.Ed25519
            else -> error("Unknown key type in jwk $jwk")
        }

    actual override suspend fun getKeyId(): String {
        return _keyId ?: _jwkObj?.get("kid")?.jsonPrimitive?.content
        ?: error("Kid not found in $jwk")
    }

    actual override suspend fun getThumbprint(): String {
        throw UnsupportedOperationException("Not implemented for macOS")
    }

    actual override suspend fun exportJWK(): String =
        _jwkObj?.toString() ?: throw IllegalStateException("JWK not initialized")

    actual override suspend fun exportJWKObject(): JsonObject =
        _jwkObj ?: throw IllegalStateException("JWK not initialized")

    actual override suspend fun exportPEM(): String {
        throw UnsupportedOperationException("Not implemented for macOS")
    }

    actual override suspend fun signRaw(plaintext: ByteArray): ByteArray {
        throw UnsupportedOperationException("Not implemented for macOS")
    }

    actual override suspend fun signJws(
        plaintext: ByteArray,
        headers: Map<String, JsonElement>
    ): String {
        throw UnsupportedOperationException("Not implemented for macOS")
    }

    actual override suspend fun verifyRaw(
        signed: ByteArray,
        detachedPlaintext: ByteArray?
    ): Result<ByteArray> {
        return Result.failure(UnsupportedOperationException("Not implemented for macOS"))
    }

    actual override suspend fun verifyJws(signedJws: String): Result<JsonElement> {
        return Result.failure(UnsupportedOperationException("Not implemented for macOS"))
    }

    actual override suspend fun getPublicKey(): JWKKey {
        val jwkObj = _jwkObj ?: throw IllegalStateException("JWK not initialized")
        return jwkObj.toMap()
            .filterKeys { it !in privateParameters }
            .let { map -> JsonObject(map.mapValues { it.value }) }
            .toString()
            .let { JWKKey(it, null) }
    }

    actual override suspend fun getPublicKeyRepresentation(): ByteArray {
        throw UnsupportedOperationException("Not implemented for macOS")
    }

    actual override suspend fun getMeta(): JwkKeyMeta {
        return JwkKeyMeta(
            getKeyId()
        )
    }

    actual override suspend fun deleteKey(): Boolean = true

    actual override val hasPrivateKey: Boolean
        get() = _jwkObj?.toMap()?.any { it.key in privateParameters } ?: false

    actual companion object : JWKKeyCreator {
        actual override suspend fun generate(type: KeyType, metadata: JwkKeyMeta?): JWKKey {
            throw UnsupportedOperationException("Not implemented for macOS")
        }

        actual override suspend fun importRawPublicKey(
            type: KeyType,
            rawPublicKey: ByteArray,
            metadata: JwkKeyMeta?
        ): Key {
            throw UnsupportedOperationException("Not implemented for macOS")
        }

        actual override suspend fun importJWK(jwk: String): Result<JWKKey> {
            return runCatching {
                JWKKey(jwk, null).apply { init() }
            }
        }

        actual override suspend fun importPEM(pem: String): Result<JWKKey> {
            return Result.failure(UnsupportedOperationException("Not implemented for macOS"))
        }
    }
}

