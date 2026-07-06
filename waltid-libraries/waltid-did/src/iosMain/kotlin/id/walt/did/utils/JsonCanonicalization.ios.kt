package id.walt.did.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

actual object JsonCanonicalization {
    actual fun getCanonicalBytes(json: String): ByteArray = getCanonicalString(json).encodeToByteArray()

    actual fun getCanonicalString(json: String): String = Json.parseToJsonElement(json).canonicalize()
}

private fun JsonElement.canonicalize(): String = when (this) {
    is JsonObject -> entries
        .sortedBy { it.key }
        .joinToString(separator = ",", prefix = "{", postfix = "}") { (key, value) ->
            "${JsonPrimitive(key)}:${value.canonicalize()}"
        }

    is JsonArray -> joinToString(separator = ",", prefix = "[", postfix = "]") { it.canonicalize() }
    else -> toString()
}
