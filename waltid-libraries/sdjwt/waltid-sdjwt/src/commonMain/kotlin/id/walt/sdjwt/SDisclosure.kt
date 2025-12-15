package id.walt.sdjwt

import id.walt.sdjwt.utils.SdjwtStringUtils.decodeFromBase64Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Selective Disclosure for a given payload field. Contains salt, field key and field value.
 * @param disclosure  The encoded disclosure, as given in the SD-JWT token.
 * @param salt  Salt value
 * @param key Field key
 * @param value Field value
 */
@ConsistentCopyVisibility
@OptIn(ExperimentalJsExport::class)
@JsExport
data class SDisclosure internal constructor(
    val disclosure: String,
    val salt: String,
    val key: String,
    val value: JsonElement
) {
    companion object {
        /**
         * Parse an encoded disclosure string
         */
        @OptIn(ExperimentalEncodingApi::class)
        fun parse(disclosure: String) =
            Json.parseToJsonElement(disclosure.decodeFromBase64Url().decodeToString()).jsonArray.let {
                if (it.size != 3) {
                    throw Exception("Invalid selective disclosure")
                }
                SDisclosure(
                    disclosure = disclosure,
                    salt = it[0].jsonPrimitive.content,
                    key = it[1].jsonPrimitive.content,
                    value = it[2]
                )
            }
    }
}
