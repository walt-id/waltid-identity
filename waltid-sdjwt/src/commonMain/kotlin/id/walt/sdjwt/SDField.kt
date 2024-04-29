package id.walt.sdjwt

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Selective disclosure information for a given payload field
 * @param sd          **Issuance:** field is made selectively disclosable if *true*, **Presentation:** field should be _disclosed_ if *true*, or _undisclosed_ if *false*
 * @param children    Not null, if field is an object. Contains SDMap for the properties of the object
 * @see SDMap
 */
@Suppress("NON_EXPORTABLE_TYPE")
@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class SDField(
    val sd: Boolean,
    val children: SDMap? = null
) {

    @JsExport.Ignore
    fun toJSON(): JsonObject {
        return buildJsonObject {
            put("sd", sd)
            children?.also {
                put("children", it.toJSON())
            }
        }
    }

    @JsExport.Ignore
    companion object {
        @JsExport.Ignore
        fun fromJSON(json: JsonElement): SDField {
            println("Parsing SDField from $json")
            return SDField(
                sd = json.jsonObject["sd"]?.jsonPrimitive?.boolean ?: throw Exception("Error parsing SDField.sd from JSON element"),
                children = json.jsonObject["children"]?.let { children ->
                    if (children is JsonObject) {
                        children.jsonObject.let { SDMap.fromJSON(it) }
                    } else if (children is JsonNull) {
                        null
                    } else {
                        throw Exception("Error parsing SDField.children from JSON element")
                    }
                }
            )
        }
    }
}
