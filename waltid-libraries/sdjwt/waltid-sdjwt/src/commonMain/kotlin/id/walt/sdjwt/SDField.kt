package id.walt.sdjwt

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

private val log = KotlinLogging.logger { }

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
    val children: SDMap? = null,
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
            log.trace { "Parsing SDField from $json" }
            return SDField(
                sd = json.jsonObject["sd"]?.jsonPrimitive?.boolean
                    ?: error("Error parsing SDField.sd from JSON element"),
                children = json.jsonObject["children"]?.let { children ->
                    when (children) {
                        is JsonObject -> children.jsonObject.let { SDMap.fromJSON(it) }
                        is JsonNull -> null
                        else -> error("Error parsing SDField.children from JSON element")
                    }
                }
            )
        }
    }
}
