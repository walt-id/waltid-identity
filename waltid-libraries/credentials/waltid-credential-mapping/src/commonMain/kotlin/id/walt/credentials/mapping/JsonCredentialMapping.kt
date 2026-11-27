package id.walt.credentials.mapping

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class JsonCredentialMapping(
    private val appendArrays: Boolean,
    private val evaluateTemplatesInArrays: Boolean,
    private val objectReplacesNonObject: Boolean,
    private val emptyObjectSetsKey: Boolean,
    private val evaluate: suspend (String) -> JsonElement,
) {
    companion object {
        fun isTemplate(value: String): Boolean =
            value.length > 2 && value.first() == '<' && value.last() == '>' && !value.contains(' ')
    }

    private suspend fun template(value: JsonPrimitive): JsonElement =
        if (value.isString && isTemplate(value.content)) evaluate(value.content) else value

    /** Writes the [mapping] of [key] into [data]. */
    suspend fun mergeInto(data: MutableMap<String, JsonElement>, key: String, mapping: JsonElement) {
        when (mapping) {
            is JsonPrimitive -> data[key] = template(mapping)

            is JsonObject -> {
                if (mapping.isEmpty() && !emptyObjectSetsKey) return
                val existing = data[key]
                val target = when {
                    existing == null -> mutableMapOf()
                    existing is JsonObject -> existing.toMutableMap()
                    objectReplacesNonObject -> mutableMapOf()
                    else -> {
                        val cause = runCatching { existing.jsonObject }.exceptionOrNull()
                        throw IllegalArgumentException(
                            "Invalid mapping for credential, when processing \"$key\": ${cause?.message}",
                            cause
                        )
                    }
                }
                mapping.forEach { (childKey, childMapping) -> mergeInto(target, childKey, childMapping) }
                data[key] = JsonObject(target)
            }

            is JsonArray -> {
                val items = if (evaluateTemplatesInArrays) evaluated(mapping) else mapping
                val existing = data[key]
                data[key] = if (appendArrays && existing is JsonArray) JsonArray(existing + items) else items
            }
        }
    }

    private suspend fun evaluated(mapping: JsonArray): JsonArray = JsonArray(mapping.map { item ->
        when (item) {
            is JsonPrimitive -> template(item)
            is JsonObject -> JsonObject(mutableMapOf<String, JsonElement>().also { target ->
                item.forEach { (k, v) -> mergeInto(target, k, v) }
            })
            is JsonArray -> evaluated(item)
        }
    })
}
