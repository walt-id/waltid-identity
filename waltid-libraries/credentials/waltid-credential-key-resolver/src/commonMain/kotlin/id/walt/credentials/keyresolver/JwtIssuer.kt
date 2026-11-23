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
    val outer = identity(payload["iss"])
    val vc = (payload["vc"] as? JsonObject)?.takeIf { "@context" in it || "type" in it }
    val w3c = if ("vct" in payload) null else vc ?: payload.takeIf { "@context" in it || "type" in it }
    val inner = identity(w3c?.get("issuer"))
    require(outer == null || inner == null || outer == inner) { "Conflicting JWT and credential issuer claims" }
    return outer ?: inner ?: identity(payload["issuer"])
}
