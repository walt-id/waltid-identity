package id.walt.sdjwt

import id.walt.sdjwt.DecoyMode.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.js.JsExport

/**
 * Mode for adding decoy digests on SD-JWT issuance
 * @property NONE: no decoy digests to be added (or mode is unknown, e.g. when parsing SD-JWTs)
 * @property FIXED: Fixed number of decoy digests to be added
 * @property RANDOM: Random number of decoy digests to be added
 */
@OptIn(kotlin.js.ExperimentalJsExport::class)
@JsExport
@Serializable
enum class DecoyMode {
    NONE,
    FIXED,
    RANDOM;

    @JsExport.Ignore
    companion object {
        @JsExport.Ignore
        fun fromJSON(json: JsonElement): DecoyMode {
            println("Parsing DecoyMode from $json")
            return (if (json is JsonObject) {
                json.jsonObject["name"]?.jsonPrimitive?.content
            } else {
                json.jsonPrimitive.content
            })?.let { valueOf(it) } ?: throw Exception("Error parsing DecoyMode from JSON value")
        }
    }
}
