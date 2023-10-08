package id.walt.core.crypto.utils

import kotlinx.serialization.json.*

object JsonUtils {

    fun Any?.toJsonElement(): JsonElement = when (this) {
        is JsonElement -> this
        null -> JsonNull
        is Map<*, *> -> this.toJsonElement()
        is List<*> -> this.toJsonElement()
        is Boolean -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        is Enum<*> -> JsonPrimitive(this.toString())
        else -> throw IllegalStateException("Can't serialize unknown collection type: $this")
    }

    fun List<*>.toJsonElement(): JsonElement {
        return JsonArray(map { it.toJsonElement() })
    }

    fun Map<*, *>.toJsonElement(): JsonElement {
        val map: MutableMap<String, JsonElement> = mutableMapOf()
        this.forEach { (key, value) ->
            map[key as String] = value.toJsonElement()
        }
        return JsonObject(map)
    }
}
