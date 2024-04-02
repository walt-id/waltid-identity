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
        var element: JsonElement? = json.toJsonElement()
        for (i in it) {
            element = when (element) {
                is JsonObject -> element[i]
                is JsonArray -> element[0].jsonObject[i]
                else -> element?.jsonPrimitive
            }
        }
        element
    }
}