package id.walt.issuer.utils

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*

object JsonUtils {

    @OptIn(ExperimentalSerializationApi::class)
    fun Any?.toJsonElement(): JsonElement =
        when (this) {
            is JsonElement -> this
            null -> JsonNull
            is String -> JsonPrimitive(this)
            is Boolean -> JsonPrimitive(this)
            is Number -> JsonPrimitive(this)
            is UByte -> JsonPrimitive(this)
            is UInt -> JsonPrimitive(this)
            is ULong -> JsonPrimitive(this)
            is UShort -> JsonPrimitive(this)
            is Map<*, *> -> JsonObject(map { Pair(it.key.toString(), it.value.toJsonElement()) }.toMap())
            is List<*> -> JsonArray(map { it.toJsonElement() })
            is Array<*> -> JsonArray(map { it.toJsonElement() })
            is Collection<*> -> JsonArray(map { it.toJsonElement() })
            else -> throw IllegalArgumentException("Unknown type: ${this::class.qualifiedName}, was: $this")
        }
}
