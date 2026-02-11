@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.credentials.signatures.sdjwt

import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.kotlincrypto.hash.sha2.SHA256

@Serializable
data class SdJwtSelectiveDisclosure(
    val salt: String,
    /** claim name */
    val name: String?,
    /** claim value */
    val value: JsonElement,

    val location: String? = null,
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

    // Secondary constructor used by parser
    constructor(jsonArray: JsonArray, encoded: String) : this(
        salt = jsonArray[0].jsonPrimitive.content,
        name = if (jsonArray.size == 3) jsonArray[1].jsonPrimitive.content else null,
        value = if (jsonArray.size == 3) jsonArray[2] else jsonArray[1],
        encoded = encoded
    )
}
