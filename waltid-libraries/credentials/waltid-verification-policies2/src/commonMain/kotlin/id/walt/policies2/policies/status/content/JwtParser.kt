package id.walt.policies2.policies.status.content

import id.walt.policies2.policies.Base64Utils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class JwtParser : ContentParser<String, JsonObject> {

    override fun parse(response: String): JsonObject = response.substringAfter(".").substringBefore(".")
        .let { Json.decodeFromString<JsonObject>(Base64Utils.decode(it).decodeToString()) }
}