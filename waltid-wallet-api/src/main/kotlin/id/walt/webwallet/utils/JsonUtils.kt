package id.walt.webwallet.utils

import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*

object JsonUtils {

    fun Map<String, Any?>.toJsonPrimitives() = this.mapValues {
        it.value.toJsonPrimitive()
    }

    fun Any?.toJsonPrimitive() =
        @OptIn(ExperimentalSerializationApi::class)
        when (this) {
            is Boolean -> JsonPrimitive(this)
            is Number -> JsonPrimitive(this)
            is String -> JsonPrimitive(this)
            null -> JsonPrimitive(null)
            else -> throw IllegalArgumentException("Unknown type for: $this")
        }

    /**
     * Attempts to extract the value identified by [key] from [json]
     * @param json the json object to parse
     * @param key the key to look for (dot-notation, e.g. "root.nested.property")
     * @return the [JsonElement] value of the [key] if found, otherwise null
     */
    fun tryGetData(json: JsonObject?, key: String): JsonElement? = key.split('.').let {
        var element: JsonElement? = json?.toJsonElement()
        for (i in it) {
            element = when (element) {
                is JsonObject -> element[i]
                is JsonArray -> element.firstOrNull {
                    it.jsonObject.containsKey(i)
                }?.let {
                    it.jsonObject[i]
                }

                else -> element?.jsonPrimitive
            }
        }
        element
    }
}