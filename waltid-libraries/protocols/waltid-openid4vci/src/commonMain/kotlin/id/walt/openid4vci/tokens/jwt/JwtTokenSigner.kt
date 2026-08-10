package id.walt.openid4vci.tokens.jwt

import id.walt.crypto2.jose.CompactJws
import io.ktor.utils.io.core.*
import kotlinx.serialization.json.*

/**
 * JWT signer that follows callback-based key resolution.
 */
internal class JwtTokenSigner private constructor(
    private val resolver: JwtSigningKeyResolver?,
    private val crypto2Resolver: Crypto2JwtSigningKeyResolver?,
    private val json: Json = Json { encodeDefaults = true },
) {

    constructor(
        resolver: JwtSigningKeyResolver,
        json: Json = Json { encodeDefaults = true },
    ) : this(resolver, null, json)

    constructor(
        crypto2Resolver: Crypto2JwtSigningKeyResolver,
        json: Json = Json { encodeDefaults = true },
    ) : this(null, crypto2Resolver, json)

    suspend fun sign(
        claims: Map<String, Any?>,
        header: Map<String, Any?> = emptyMap(),
    ): String {
        crypto2Resolver?.let { resolver ->
            val resolved = resolver.resolveSigningKey()
            header[JwtHeaderParams.KEY_ID]?.let { configured ->
                require(configured.toString() == resolved.keyId) {
                    "JWT kid header conflicts with the resolved signing key"
                }
            }
            val protectedHeader = buildJsonObject {
                header.forEach { (name, value) -> put(name, value.toJsonElement()) }
                put(JwtHeaderParams.TYPE, JsonPrimitive("JWT"))
                put(JwtHeaderParams.KEY_ID, JsonPrimitive(resolved.keyId))
            }
            val payload = claims.toJsonObject()
            return CompactJws.sign(
                payload = json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray(),
                key = resolved.key,
                algorithm = resolved.algorithm,
                protectedHeader = protectedHeader,
            )
        }

        val key = requireNotNull(resolver).resolveSigningKey()

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
