package id.walt.crypto.keys

import id.walt.crypto.exceptions.KeyTypeMissingException
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

actual fun resolveSerializedKeyBlocking(json: JsonObject): Key {
    val type = json["type"]?.jsonPrimitive?.content
        ?: throw KeyTypeMissingException()
    require(type == "jwk") { "Synchronous JS key deserialization only supports JWK keys" }
    return Json.decodeFromJsonElement(
        JWKKey.serializer(),
        JsonObject(json.filterKeys { it != "type" }),
    )
}
