package id.walt.credentials.keyresolver

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Use one issuer identity for credential policy checks and signature key resolution. */
fun resolveJwtIssuer(payload: JsonObject): String? {
    fun identity(value: JsonElement?): String? = when (value) {
        is JsonPrimitive -> value.contentOrNull
        is JsonObject -> (value["id"] as? JsonPrimitive)?.contentOrNull
        else -> null
    }
    val outer = payload["iss"]?.let { value ->
        require(value is JsonPrimitive && value.isString) { "JWT iss must be a string" }
        value.content
    }
    val issuers = generateSequence(payload) { it["vc"] as? JsonObject }
        .takeWhile { "vct" !in it }
        .mapNotNull { identity(it["issuer"]) }
        .toList()
    require((listOfNotNull(outer) + issuers).distinct().size <= 1) {
        "Conflicting JWT and credential issuer claims"
    }
    return outer ?: issuers.firstOrNull()
}

/** Select the same W3C object for SD-JWT format detection and issuer interpretation. */
fun w3cJwtPayload(payload: JsonObject): JsonObject? =
    generateSequence(payload) { it["vc"] as? JsonObject }
        .takeWhile { "vct" !in it }
        .lastOrNull { data -> listOf("@context", "type", "validFrom", "issuanceDate").any { it in data } }
