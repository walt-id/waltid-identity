package id.walt.webwallet.seeker

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DefaultCredentialTypeSeeker : Seeker<String> {
    override fun get(data: JsonObject): String =
        data.jsonObject["type"]?.jsonArray?.last()?.jsonPrimitive?.content ?: "n/a"
}