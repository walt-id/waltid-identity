@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package id.walt.openid4vci.tokens.jwt

import io.ktor.utils.io.core.*
import kotlinx.serialization.json.*

/**
 * JWT signer that follows callback-based key resolution.
 */
internal class JwtTokenSigner(
    private val resolver: JwtSigningKeyResolver,
    private val json: Json = Json { encodeDefaults = true },
) {

    suspend fun sign(
        claims: Map<String, Any?>,
        header: Map<String, Any?> = emptyMap(),
    ): String {
        val key = resolver.resolveSigningKey()

        val protectedHeader = buildJsonObject {
            put(JwtHeaderParams.TYPE, JsonPrimitive("JWT"))
            put(JwtHeaderParams.ALGORITHM, JsonPrimitive(key.keyType.jwsAlg))
            put(JwtHeaderParams.KEY_ID, JsonPrimitive(key.getKeyId()))
            header.forEach { (k, v) -> put(k, v.toJsonElement()) }
        }

        val payloadElement = claims.toJsonObject()

        return key.signJws(payloadElement.toString().toByteArray(), protectedHeader)
    }

    private fun Map<String, Any?>.toJsonObject(): JsonObject =
        JsonObject(mapValues { (_, value) -> value.toJsonElement() })

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        is Iterable<*> -> buildJsonArray { this@toJsonElement.forEach { add(it.toJsonElement()) } }
        is Map<*, *> -> JsonObject(this.entries.associate { (k, v) -> k.toString() to v.toJsonElement() })
        else -> JsonPrimitive(this.toString())
    }

}
