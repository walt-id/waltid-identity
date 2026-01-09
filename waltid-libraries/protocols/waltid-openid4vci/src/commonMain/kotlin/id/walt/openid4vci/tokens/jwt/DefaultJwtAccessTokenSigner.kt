@file:OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)

package id.walt.openid4vci.tokens.jwt

import io.ktor.utils.io.core.toByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray

/**
 * Default JWT signer that follows callback-based key resolution.
 * It chooses the JWS algorithm from the key metadata,
 * builds a compact JWT, and exposes both the token and signature.
 */
open class DefaultJwtAccessTokenSigner(
    private val resolver: JwtSigningKeyResolver,
    private val json: Json = Json { encodeDefaults = true },
) : JwtAccessTokenSigner {

    override suspend fun sign(
        claims: Map<String, Any?>,
        header: Map<String, Any?>,
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
