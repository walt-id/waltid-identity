package id.walt.utils

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonPrimitive

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
}
