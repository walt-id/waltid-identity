package id.walt.credentials.signatures.sdjwt

import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.kotlincrypto.hash.sha2.SHA256

data class SdJwtSelectiveDisclosure(
    val salt: String,
    val name: String,
    val value: JsonElement
) {
    fun asJsonArray() = JsonArray(listOf(JsonPrimitive(salt), JsonPrimitive(name), value))

    fun encoded() = asJsonArray().toString().encodeToByteArray().encodeToBase64Url()
    fun asHashed() = SHA256().digest(asJsonArray().toString().encodeToByteArray()).encodeToBase64Url()

    constructor(jsonArray: JsonArray) : this(
        salt = jsonArray[0].jsonPrimitive.content,
        name = jsonArray[1].jsonPrimitive.content,
        value = jsonArray[2]
    )
}
