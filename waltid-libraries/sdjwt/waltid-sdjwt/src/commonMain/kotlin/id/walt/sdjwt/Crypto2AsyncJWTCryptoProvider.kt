package id.walt.sdjwt

import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.Key
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class Crypto2SdJwtKey(
    val key: Key,
    val algorithm: JwsAlgorithm,
    val keyId: String = key.id.value,
) {
    init {
        require(keyId.isNotBlank()) { "SD-JWT key ID cannot be blank" }
        require(key.capabilities.supportsSignatureAlgorithm(algorithm.toSignatureAlgorithm())) {
            "SD-JWT key does not support ${algorithm.identifier}"
        }
    }
}

class Crypto2AsyncJWTCryptoProvider(
    keys: Map<String, Crypto2SdJwtKey>,
) : AsyncJWTCryptoProvider {
    private val keys = keys.toMap().also { configured ->
        require(configured.isNotEmpty()) { "At least one SD-JWT key is required" }
        require(configured.values.map(Crypto2SdJwtKey::keyId).distinct().size == configured.size) {
            "SD-JWT key IDs must be unique"
        }
    }

    override suspend fun sign(
        payload: JsonObject,
        keyID: String?,
        typ: String,
        headers: Map<String, Any>,
    ): String {
        val resolved = resolveSigningKey(keyID)
        headers["kid"]?.let { require(it.toString() == resolved.keyId) { "SD-JWT kid header conflicts with signing key" } }
        val protectedHeader = buildJsonObject {
            headers.forEach { (name, value) -> put(name, value.toJsonElement()) }
            put("kid", resolved.keyId)
            put("typ", typ)
        }
        return CompactJws.sign(
            payload = Json.encodeToString(payload).encodeToByteArray(),
            key = resolved.key,
            algorithm = resolved.algorithm,
            protectedHeader = protectedHeader,
        )
    }

    override suspend fun verify(jwt: String): JwtVerificationResult = verify(jwt, expectedKeyId = null)

    suspend fun verify(jwt: String, expectedKeyId: String?): JwtVerificationResult {
        return try {
            val decoded = CompactJws.decodeUnverified(jwt)
            val kid = decoded.protectedHeader["kid"]?.let { it as? JsonPrimitive }?.content
            val resolved = resolveVerificationKey(kid)
            expectedKeyId?.let { expected ->
                val expectedKey = resolveById(expected)
                    ?: throw IllegalArgumentException("No key found for key ID: $expected")
                require(expectedKey == resolved) { "JWT kid does not match requested key ID" }
            }
            require(decoded.algorithm == resolved.algorithm) {
                "SD-JWT algorithm does not match configured key algorithm"
            }
            CompactJws.verify(jwt, resolved.key, resolved.algorithm)
            JwtVerificationResult(verified = true, message = "Signature verified")
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Throwable) {
            JwtVerificationResult(verified = false, message = cause.message ?: "Signature verification failed")
        }
    }

    private fun resolveSigningKey(keyId: String?): Crypto2SdJwtKey = when {
        keyId != null -> resolveById(keyId) ?: throw IllegalArgumentException("No key found for key ID: $keyId")
        keys.size == 1 -> keys.values.single()
        else -> throw IllegalArgumentException("No key ID provided")
    }

    private fun resolveVerificationKey(keyId: String?): Crypto2SdJwtKey = when {
        keyId != null -> resolveById(keyId) ?: throw IllegalArgumentException("No key found for key ID: $keyId")
        keys.size == 1 -> keys.values.single()
        else -> throw IllegalArgumentException("No key ID found in JWT header")
    }

    private fun resolveById(keyId: String): Crypto2SdJwtKey? =
        keys[keyId] ?: keys.values.singleOrNull { it.keyId == keyId }
}

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is String -> JsonPrimitive(this)
    is Iterable<*> -> buildJsonArray { this@toJsonElement.forEach { add(it.toJsonElement()) } }
    is Map<*, *> -> JsonObject(entries.associate { (key, value) -> key.toString() to value.toJsonElement() })
    else -> JsonPrimitive(toString())
}
