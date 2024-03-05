package id.walt.webwallet.seeker

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class DefaultDidSeeker : Seeker<String> {
    override fun get(data: JsonObject): String = data.jsonObject["iss"]?.jsonPrimitive?.content ?: "n/a"
}