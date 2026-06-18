package id.waltid.openid4vp.wallet.request

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

object AuthorizationRequestParameterCodec {
    fun parse(json: Json, value: String): JsonElement =
        // Authorization request query parameters arrive as strings, even when they look like JSON scalars.
        // Only parse values that are explicitly JSON-encoded so fields like nonce/state/client_id stay strings.
        if (!isJsonEncoded(value)) JsonPrimitive(value)
        else runCatching { json.parseToJsonElement(value) }.getOrElse { JsonPrimitive(value) }

    fun encode(json: Json, value: JsonElement): String =
        when (value) {
            is JsonPrimitive -> value.content
            else -> json.encodeToString(JsonElement.serializer(), value)
        }

    private fun isJsonEncoded(value: String): Boolean =
        value.trimStart().run { startsWith("{") || startsWith("[") || startsWith("\"") }
}
