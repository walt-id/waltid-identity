package id.walt.sdjwt

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Selective-disclosure descriptor for a JSON array. Each entry in [elements] controls the
 * corresponding index in the source array; [wildcard], if set, applies to any index not
 * covered by [elements] (e.g. with a path like `"colors.[]"`).
 */
@Suppress("NON_EXPORTABLE_TYPE")
@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
class SDArray(
    val elements: List<SDField> = emptyList(),
    val wildcard: SDField? = null,
    val decoyMode: DecoyMode = DecoyMode.NONE,
    val decoys: Int = 0,
) {
    init {
        require(decoys >= 0) { "decoys must be non-negative, got $decoys" }
    }

    @JsExport.Ignore
    fun toJSON(): JsonObject = buildJsonObject {
        put("elements", buildJsonArray {
            elements.forEach { add(it.toJSON()) }
        })
        wildcard?.also { put("wildcard", it.toJSON()) }
        put("decoyMode", decoyMode.name)
        put("decoys", decoys)
    }

    @JsExport.Ignore
    companion object {
        @JsExport.Ignore
        fun fromJSON(json: JsonObject): SDArray {
            val elements = json["elements"]?.jsonArray
                ?.map { SDField.fromJSON(it) }
                ?: emptyList()
            val wildcard = json["wildcard"]?.let { SDField.fromJSON(it.jsonObject) }
            val decoyMode = json["decoyMode"]?.let { DecoyMode.fromJSON(it) } ?: DecoyMode.NONE
            val decoys = json["decoys"]?.jsonPrimitive?.int ?: 0
            return SDArray(elements, wildcard, decoyMode, decoys)
        }

        @JsExport.Ignore
        fun fromJSON(json: String): SDArray =
            fromJSON(Json.parseToJsonElement(json).jsonObject)
    }
}
