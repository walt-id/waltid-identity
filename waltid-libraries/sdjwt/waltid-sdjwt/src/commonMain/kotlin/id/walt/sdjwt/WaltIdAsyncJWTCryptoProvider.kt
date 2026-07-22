package id.walt.sdjwt

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Deprecated("Use Crypto2AsyncJWTCryptoProvider")
class WaltIdAsyncJWTCryptoProvider(private val keys: Map<String, Key>) : AsyncJWTCryptoProvider {
    override suspend fun sign(
        payload: JsonObject,
        keyID: String?,
        typ: String,
        headers: Map<String, Any>
    ): String {
        val key = resolveSigningKey(keyID)
        if (!key.hasPrivateKey) throw IllegalArgumentException("Key has no private key")
        val allHeaders = mapOf("kid" to key.getKeyId(), "typ" to typ).plus(headers)
        return key.signJws(
            plaintext = payload.toString().encodeToByteArray(),
            headers = allHeaders.mapValues { it.value.toJsonElement() },
        )
    }

    override suspend fun verify(jwt: String): JwtVerificationResult {
        val key = resolveVerificationKey(jwt)
        return key.verifyJws(jwt).let {
            JwtVerificationResult(
                verified = it.isSuccess,
                message = it.toString(),
            )
        }
    }

    private fun resolveHeaderKeyId(jwt: String): String? = runCatching {
        Json.parseToJsonElement(jwt.substringBefore(".").base64UrlDecode().decodeToString())
            .jsonObject["kid"]?.jsonPrimitive?.content
    }.getOrNull()

    private fun resolveSigningKey(keyID: String?): Key {
        return when {
            keyID != null -> keys[keyID] ?: throw IllegalArgumentException("No key found for key ID: $keyID")
            keys.size == 1 -> keys.values.single()
            else -> throw IllegalArgumentException("No key ID provided")
        }
    }

    private fun resolveVerificationKey(jwt: String): Key {
        val kid = resolveHeaderKeyId(jwt)
        return when {
            kid != null -> keys[kid] ?: throw IllegalArgumentException("No key found for key ID: $kid")
            keys.size == 1 -> keys.values.single()
            else -> throw IllegalArgumentException("No key ID found in JWT header")
        }
    }
}
