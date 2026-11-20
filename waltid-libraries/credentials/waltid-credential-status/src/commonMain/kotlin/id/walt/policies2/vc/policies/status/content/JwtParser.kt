package id.walt.policies2.vc.policies.status.content

import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class JwtParser : ContentParser<String, JsonObject> {

    override fun parse(response: String): JsonObject = response.substringAfter(".").substringBefore(".")
        .let { Json.decodeFromString<JsonObject>(it.base64UrlDecode().decodeToString()) }
}
