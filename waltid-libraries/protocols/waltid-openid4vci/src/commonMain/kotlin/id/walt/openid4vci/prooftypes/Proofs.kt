package id.walt.openid4vci.prooftypes

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Proofs object for the OpenID4VCI credential request.
 * Only JWT proofs are supported by the handlers for now, but the model keeps the other
 * proof types to stay aligned with the specification.
 */
data class Proofs(
    val jwt: List<String>? = null,
    val diVp: List<JsonObject>? = null,
    val attestation: List<String>? = null,
    val additional: Map<String, JsonElement> = emptyMap(),
) {
    companion object {
        fun fromJsonObject(json: JsonObject): Proofs {
            val jwt = json["jwt"]?.let { parseStringArray("jwt", it) }
            val diVp = json["di_vp"]?.let { parseObjectArray("di_vp", it) }
            val attestation = json["attestation"]?.let { parseStringArray("attestation", it) }
            val additional = json.filterKeys { it != "jwt" && it != "di_vp" && it != "attestation" }
            return Proofs(jwt = jwt, diVp = diVp, attestation = attestation, additional = additional)
        }

        private fun parseStringArray(name: String, element: JsonElement): List<String> {
            val array = element as? JsonArray
                ?: throw IllegalArgumentException("$name must be a JSON array")
            if (array.isEmpty()) throw IllegalArgumentException("$name must be a non-empty array")
            val values = array.map { it.jsonPrimitive.content }
            if (values.any { it.isBlank() }) {
                throw IllegalArgumentException("$name must not contain blank values")
            }
            return values
        }

        private fun parseObjectArray(name: String, element: JsonElement): List<JsonObject> {
            val array = element as? JsonArray
                ?: throw IllegalArgumentException("$name must be a JSON array")
            if (array.isEmpty()) throw IllegalArgumentException("$name must be a non-empty array")
            return array.map { it.jsonObject }
        }
    }
}
