package id.walt.w3c.vc.vcs

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private val v2ContextUrl = W3CV2DataModel.defaultContext.first()
private val v1ContextUrl = W3CV11DataModel.defaultContext.first()

/** The URL string of a `@context` entry, or null when the entry is an inline context object. */
fun JsonElement.w3cContextUrl(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content

/**
 * Context URLs from a W3C `@context` value.
 *
 * Returns null when the value is neither a string nor an array. Inline JSON-LD objects in an
 * array are omitted; they are valid and are not version indicators.
 */
fun JsonElement.w3cContextUrls(): List<String>? = when (this) {
    is JsonArray -> mapNotNull { it.w3cContextUrl() }
    is JsonPrimitive -> if (isString) listOf(content) else null
    else -> null
}

private fun JsonElement.w3cContextEntries(): List<JsonElement> = when (this) {
    is JsonArray -> this
    is JsonPrimitive, is JsonObject -> listOf(this)
    else -> emptyList()
}

/**
 * Prepares a credential map for VC Data Model 2.0 issuance.
 *
 * Inline context objects stay in place. The v2 context URL is added when absent, and the v1
 * context URL is removed. `issuanceDate` and `expirationDate` move to `validFrom` and
 * `validUntil` when those v2 names are not already set.
 */
fun MutableMap<String, JsonElement>.applyIssuedV2Context(): MutableMap<String, JsonElement> {
    val entries = this["@context"]?.w3cContextEntries().orEmpty()
    val hasV2 = entries.any { it.w3cContextUrl() == v2ContextUrl }
    val withoutV1 = entries.filter { it.w3cContextUrl() != v1ContextUrl }
    this["@context"] = JsonArray(if (hasV2) withoutV1 else listOf(JsonPrimitive(v2ContextUrl)) + withoutV1)
    remove("issuanceDate")?.let { value -> if ("validFrom" !in this) this["validFrom"] = value }
    remove("expirationDate")?.let { value -> if ("validUntil" !in this) this["validUntil"] = value }
    return this
}
