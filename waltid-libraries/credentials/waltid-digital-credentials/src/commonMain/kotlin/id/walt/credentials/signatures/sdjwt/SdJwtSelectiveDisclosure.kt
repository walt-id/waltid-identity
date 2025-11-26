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
    val name: String,
    val value: JsonElement,

    val location: String? = null,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    var encoded: String = makeEncoded(salt, name, value)
) {
    companion object {
        fun makeJsonArray(salt: String, name: String, value: JsonElement) =
            JsonArray(listOf(JsonPrimitive(salt), JsonPrimitive(name), value))

        fun encodeJsonArray(jsonArray: JsonArray) = jsonArray.toString().encodeToByteArray().encodeToBase64Url()
        fun makeEncoded(salt: String, name: String, value: JsonElement) = encodeJsonArray(makeJsonArray(salt, name, value))

        fun encodeJsonArray2(jsonArray: JsonArray) = jsonArray.toString().encodeToByteArray().encodeToBase64()
        fun makeEncoded2(salt: String, name: String, value: JsonElement) = encodeJsonArray2(makeJsonArray(salt, name, value))
    }

    fun asJsonArray() = makeJsonArray(salt, name, value)
    fun asEncoded() = makeEncoded(salt, name, value)
    fun asEncoded2() = makeEncoded2(salt, name, value)
    fun asHashed() = SHA256().digest(asEncoded().encodeToByteArray()).encodeToBase64Url()
    fun asHashed2() = SHA256().digest(asEncoded2().encodeToByteArray()).encodeToBase64Url()
    fun asHashed3() = SHA256().digest(encoded.encodeToByteArray().encodeToBase64Url().encodeToByteArray()).encodeToBase64Url()

    constructor(jsonArray: JsonArray, encoded: String) : this(
        salt = jsonArray[0].jsonPrimitive.content,
        name = jsonArray[1].jsonPrimitive.content,
        value = jsonArray[2],
        encoded = encoded
    )
}
