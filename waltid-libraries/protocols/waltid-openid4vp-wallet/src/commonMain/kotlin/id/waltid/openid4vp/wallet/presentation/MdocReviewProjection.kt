@file:OptIn(ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet.presentation

import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.toIntegerJsonArrayOrNull
import id.walt.crypto.utils.toJsonObjectKey
import id.walt.mdoc.objects.document.IssuerSigned
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Project the signed CBOR structure so display serializers cannot change the paths reviewed for consent. */
internal fun IssuerSigned.toMdocReviewProjection(): PresentationClaimsProjection {
    val locations = mutableListOf<PresentationClaimLocation>()
    fun addLocation(path: List<JsonElement>, sharedBy: List<String> = emptyList(), isBinary: Boolean = false) {
        locations += PresentationClaimLocation(path, path, sharedBy, alwaysCarried = false, isBinary = isBinary)
    }

    fun project(value: CborElement, path: List<JsonElement>, sharedBy: List<String>): JsonElement {
        addLocation(path, sharedBy, isBinary = value is CborByteString)
        return when (value) {
            is CborByteString -> JsonPrimitive(value.toByteArray().encodeToBase64Url())
            is CborArray -> value.toIntegerJsonArrayOrNull()?.also {
                value.indices.forEach { index -> addLocation(path + JsonPrimitive(index), sharedBy) }
            } ?: JsonArray(value.mapIndexed { index, child -> project(child, path + JsonPrimitive(index), sharedBy) })
            is CborMap -> JsonObject(buildMap {
                for ((key, child) in value.entries) {
                    val name = key.toJsonObjectKey()
                    require(name !in this) { "An mdoc claim has conflicting JSON member names at $path" }
                    put(name, project(child, path + JsonPrimitive(name), sharedBy))
                }
            })
            else -> value.toJsonElement()
        }
    }

    addLocation(emptyList())
    val claims = JsonObject(namespaces.orEmpty().mapValues { (namespace, items) ->
        val path = listOf(JsonPrimitive(namespace))
        addLocation(path)
        JsonObject(items.entries.associate { entry ->
            val item = entry.value
            val itemPath = path + JsonPrimitive(item.elementIdentifier)
            item.elementIdentifier to project(item.elementValue, itemPath, listOf(itemPath.joinToString(".")))
        })
    })
    return PresentationClaimsProjection(claims, wholeCredential = false, locations = locations)
}
