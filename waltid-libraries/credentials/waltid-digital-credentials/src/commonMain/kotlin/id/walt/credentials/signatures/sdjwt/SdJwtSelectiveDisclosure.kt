@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.credentials.signatures.sdjwt

import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.kotlincrypto.hash.sha2.SHA256

@Serializable
data class SdJwtSelectiveDisclosure(
    val salt: String,
    /** claim name */
    val name: String?,
    /** claim value */
    val value: JsonElement,

    /**
     * Claim Path locating this disclosure's claim within the credential, per
     * SD-JWT VC (draft-ietf-oauth-sd-jwt-vc) §4.6.1.
     *
     * A non-empty array of path components, each being:
     *  - a [JsonPrimitive] string: selects the object key,
     *  - a [JsonPrimitive] non-negative integer: selects the array index,
     *  - [kotlinx.serialization.json.JsonNull]: selects all elements of the array (wildcard).
     *
     * The path is resolved against the credential root (the top-level claims object; for
     * W3C credentials embedded under a `vc` claim, relative to the `vc` content). It points
     * to the claim as if all selectively disclosable claims were disclosed (§4.6.1.2).
     *
     * `null` when the location has not (yet) been resolved.
     */
    val location: List<JsonElement>? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    var encoded: String = makeEncoded(salt, name, value)
) {
    companion object {
        // If name is null, creates [salt, value] (size 2).
        // If name is present, creates [salt, name, value] (size 3).
        fun makeJsonArray(salt: String, name: String?, value: JsonElement): JsonArray {
            val content = mutableListOf<JsonElement>(JsonPrimitive(salt))
            if (name != null) {
                content.add(JsonPrimitive(name))
            }
            content.add(value)
            return JsonArray(content)
        }

        fun encodeJsonArray(jsonArray: JsonArray) = jsonArray.toString().encodeToByteArray().encodeToBase64Url()
        fun makeEncoded(salt: String, name: String?, value: JsonElement) = encodeJsonArray(makeJsonArray(salt, name, value))

        fun encodeJsonArray2(jsonArray: JsonArray) = jsonArray.toString().encodeToByteArray().encodeToBase64()
        fun makeEncoded2(salt: String, name: String?, value: JsonElement) = encodeJsonArray2(makeJsonArray(salt, name, value))
    }

    fun asJsonArray() = makeJsonArray(salt, name, value)
    fun asEncoded() = makeEncoded(salt, name, value)
    fun asEncoded2() = makeEncoded2(salt, name, value)
    fun asHashed() = SHA256().digest(asEncoded().encodeToByteArray()).encodeToBase64Url()
    fun asHashed2() = SHA256().digest(asEncoded2().encodeToByteArray()).encodeToBase64Url()
    fun asHashed3() = SHA256().digest(encoded.encodeToByteArray().encodeToBase64Url().encodeToByteArray()).encodeToBase64Url()

    /**
     * Renders this disclosure's [location] Claim Path (SD-JWT VC §4.6.1) as a DIF Presentation
     * Exchange JSONPath string (e.g. `$.credentialSubject.degree.name`, `$.nationalities[0]`).
     *
     * Provided for interoperability with legacy components that select claims using DIF PE
     * `field.path` JSONPath strings. Wildcard (`null`) path components are rendered as `[*]`.
     * Returns `null` when [location] has not been resolved.
     */
    fun locationAsJsonPath(): String? = location?.let { path ->
        buildString {
            append("$")
            path.forEach { segment ->
                when (segment) {
                    is JsonNull -> append("[*]")
                    is JsonPrimitive -> {
                        val intIndex = segment.intOrNull
                        if (!segment.isString && intIndex != null) append("[$intIndex]")
                        else append(".").append(segment.content)
                    }
                    else -> append(".").append(segment.toString())
                }
            }
        }
    }

    // Secondary constructor used by parser
    constructor(jsonArray: JsonArray, encoded: String) : this(
        salt = jsonArray[0].jsonPrimitive.content,
        name = if (jsonArray.size == 3) jsonArray[1].jsonPrimitive.content else null,
        value = if (jsonArray.size == 3) jsonArray[2] else jsonArray[1],
        encoded = encoded
    )
}
