package id.walt.policies.policies.status.parser

import id.walt.policies.policies.Base64Utils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class JwtParser : ContentParser<JsonObject> {

    override fun parse(response: String): JsonObject = response.substringAfter(".").substringBefore(".")
        .let { Json.decodeFromString<JsonObject>(Base64Utils.decode(it).decodeToString()) }
}