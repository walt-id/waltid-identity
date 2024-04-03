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

    //TODO: remove from WallletNotifications.kt when PR-278 (minor event and notification improvements) is merged
    fun tryGetData(json: JsonObject, key: String): JsonElement? = key.split('.').let {
        var js: JsonElement? = json.toJsonElement()
        for (i in it) {
            val element = js?.jsonObject?.get(i)
            js = when (element) {
                is JsonObject -> element
                is JsonArray -> element.jsonArray
                else -> element?.jsonPrimitive
            }
        }
        js
    }
}
