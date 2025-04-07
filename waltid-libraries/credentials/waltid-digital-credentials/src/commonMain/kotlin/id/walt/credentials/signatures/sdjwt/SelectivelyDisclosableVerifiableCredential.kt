package id.walt.credentials.signatures.sdjwt

import id.walt.credentials.formats.DigitalCredential
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive

interface SelectivelyDisclosableVerifiableCredential {
    val disclosableAttributes: JsonArray?
    val disclosuresString: String?

    fun listDisclosures() =
        disclosuresString?.split("~")?.mapNotNull {
            if (it.isNotBlank()) {
                val jsonArrayString = it.base64UrlDecode().decodeToString()
                println(jsonArrayString)
                val jsonArray = Json.decodeFromString<JsonArray>(jsonArrayString)

                SdJwtSelectiveDisclosure(
                    salt = jsonArray[0].jsonPrimitive.content,
                    name = jsonArray[1].jsonPrimitive.content,
                    value = jsonArray[2]
                )
            } else null
        }

    fun disclose(credential: DigitalCredential, attributes: List<SdJwtSelectiveDisclosure>): String {
        checkNotNull(credential.signed) { "Credential has to be signed to be able to disclose" }
        return "${credential.signed}~${attributes.joinToString("~") { it.encoded() }}"
    }
}
