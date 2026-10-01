package id.walt.openid4vci.proofs

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

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
 * The default verifier supports JWT and attestation proofs. DI VP remains an extension point.
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
    /** Also validates DTOs constructed without the HTTP parser. Counts present, even empty, fields. */
    fun normalized(): CredentialProofCollection {
        require(listOf(jwt, diVp, attestation).count { it != null } == 1) {
            "Credential request must contain exactly one proof type"
        }
        val collection = when {
            jwt != null -> CredentialProofCollection(ProofType.JWT, jwt.map(::JsonPrimitive))
            diVp != null -> CredentialProofCollection(ProofType.DI_VP, diVp.toList())
            else -> CredentialProofCollection(ProofType.ATTESTATION, requireNotNull(attestation).map(::JsonPrimitive))
        }
        require(attestation == null || attestation.size == 1) { "Attestation proofs must contain exactly one JWT" }
        require(collection.values.isNotEmpty()) { "Credential proofs must not be empty" }
        require(collection.values.none { it is JsonPrimitive && (!it.isString || it.content.isBlank()) }) {
            "Credential proofs must contain non-empty strings or objects"
        }
        return collection
    }

    companion object {
        fun fromJsonObject(json: JsonObject): Proofs {
            val unsupportedProofTypes = json.keys - supportedProofTypes
            require(unsupportedProofTypes.isEmpty()) {
                "Unsupported credential proof type: ${unsupportedProofTypes.first()}"
            }
            val jwt = json[ProofType.JWT.value]?.let { parseStringArray(ProofType.JWT.value, it) }
            val diVp = json[ProofType.DI_VP.value]?.let { parseObjectArray(ProofType.DI_VP.value, it) }
            val attestation = json[ProofType.ATTESTATION.value]?.let { parseStringArray(ProofType.ATTESTATION.value, it) }
            return Proofs(jwt = jwt, diVp = diVp, attestation = attestation).also { it.normalized() }
        }

        private val supportedProofTypes = ProofType.entries.map { it.value }.toSet()

        private fun parseStringArray(name: String, element: JsonElement): List<String> {
            val array = element as? JsonArray
                ?: throw IllegalArgumentException("$name must be a JSON array")
            if (array.isEmpty()) throw IllegalArgumentException("$name must be a non-empty array")
            val values = array.map {
                (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content
                    ?: throw IllegalArgumentException("$name must contain strings")
            }
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

data class CredentialProofCollection(val type: ProofType, val values: List<JsonElement>)
