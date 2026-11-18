@file:OptIn(ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet.presentation

import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.mdoc.objects.document.IssuerSigned
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.cbor.long
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/** Project the selected issuer-signed elements using their CBOR types and existing JSON view. */
internal fun IssuerSigned.toMdocReviewProjection(): PresentationClaimsProjection {
    val locations = mutableListOf<PresentationClaimLocation>()
    fun addLocation(path: List<JsonElement>, sharedBy: List<String> = emptyList(), isBinary: Boolean = false) {
        locations += PresentationClaimLocation(path, path, sharedBy, alwaysCarried = false, isBinary = isBinary)
    }

    fun project(
        value: JsonElement,
        source: CborElement?,
        path: List<JsonElement>,
        sharedBy: List<String>,
    ): JsonElement {
        if (source is CborByteString) {
            addLocation(path, sharedBy, isBinary = true)
            return JsonPrimitive(source.toByteArray().encodeToBase64Url())
        }
        addLocation(path, sharedBy)
        return when (value) {
            is JsonArray -> {
                val sourceArray = (source as? CborArray)?.takeIf { it.size == value.size }
                var changed: MutableList<JsonElement>? = null
                value.forEachIndexed { index, child ->
                    val projected = project(child, sourceArray?.get(index), path + JsonPrimitive(index), sharedBy)
                    if (projected !== child) {
                        val children = changed ?: value.toMutableList().also { changed = it }
                        children[index] = projected
                    }
                }
                // Keep the original packed JSON view of genuine numeric arrays.
                changed?.let(::JsonArray) ?: value
            }
            is JsonObject -> {
                val sourceKeys = (source as? CborMap)?.entries?.associate { (key, child) ->
                    // Match the generic CBOR-to-JSON key conversion.
                    val name = when (key) {
                        is CborString -> key.value
                        is CborInteger -> key.long.toString()
                        else -> key.toString()
                    }
                    name to child
                }.orEmpty()
                var changed: MutableMap<String, JsonElement>? = null
                value.forEach { (name, child) ->
                    val projected = project(child, sourceKeys[name], path + JsonPrimitive(name), sharedBy)
                    if (projected !== child) {
                        val children = changed ?: value.toMutableMap().also { changed = it }
                        children[name] = projected
                    }
                }
                changed?.let(::JsonObject) ?: value
            }
            else -> value
        }
    }

    addLocation(emptyList())
    val claims = JsonObject(namespacesToJson().mapValues { (namespace, json) ->
        val path = listOf(JsonPrimitive(namespace))
        addLocation(path)
        val items = namespaces.orEmpty().getValue(namespace).entries.associate {
            it.value.elementIdentifier to it.value.elementValue
        }
        JsonObject(json.jsonObject.mapValues { (name, value) ->
            val itemPath = path + JsonPrimitive(name)
            project(value, items.getValue(name), itemPath, listOf(itemPath.joinToString(".")))
        })
    })
    return PresentationClaimsProjection(claims, wholeCredential = false, locations = locations)
}
