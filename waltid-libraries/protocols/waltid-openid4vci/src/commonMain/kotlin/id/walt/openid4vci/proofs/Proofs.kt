package id.walt.openid4vci.proofs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val JWT_PROOF_TYPE = "jwt"
private const val DI_VP_PROOF_TYPE = "di_vp"
private const val ATTESTATION_PROOF_TYPE = "attestation"

/** Proof type identifiers (OpenID4VCI 1.0). */
enum class ProofType(val value: String) {
    JWT(JWT_PROOF_TYPE),
    DI_VP(DI_VP_PROOF_TYPE),
    ATTESTATION(ATTESTATION_PROOF_TYPE);

    override fun toString(): String = value

    companion object {
        fun fromValue(value: String): ProofType? = entries.firstOrNull { it.value == value }
    }
}

/**
 * Proofs object for the OpenID4VCI credential request.
 * Only JWT proofs are supported by the handlers for now, but the model keeps the other
 * proof types to stay aligned with the specification.
 */
@Serializable
data class Proofs(
    @SerialName(JWT_PROOF_TYPE)
    val jwt: List<String>? = null,
    @SerialName(DI_VP_PROOF_TYPE)
    val diVp: List<JsonObject>? = null,
    @SerialName(ATTESTATION_PROOF_TYPE)
    val attestation: List<String>? = null,
) {
    companion object {
        fun fromJsonObject(json: JsonObject): Proofs {
            val unsupportedProofTypes = json.keys - supportedProofTypes
            require(unsupportedProofTypes.isEmpty()) {
                "Unsupported credential proof type: ${unsupportedProofTypes.first()}"
            }
            val jwt = json[ProofType.JWT.value]?.let { parseStringArray(ProofType.JWT.value, it) }
            val diVp = json[ProofType.DI_VP.value]?.let { parseObjectArray(ProofType.DI_VP.value, it) }
            val attestation = json[ProofType.ATTESTATION.value]?.let { parseStringArray(ProofType.ATTESTATION.value, it) }
            return Proofs(jwt = jwt, diVp = diVp, attestation = attestation)
        }

        private val supportedProofTypes = ProofType.entries.map { it.value }.toSet()

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
