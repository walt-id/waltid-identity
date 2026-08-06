package id.walt.dcql.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * Represents constraints on specific claims within a credential.
 * See: Section 6.3
 */
@Serializable
data class ClaimsQuery(
    /** Required if claim_sets is present in parent CredentialQuery */
    val id: String? = null,

    /** Required non-empty path to the claim (format-specific interpretation). */
    val path: List<JsonElement>,

    /** Optional specific values to match */
    val values: List<JsonPrimitive>? = null,

    /** defined at https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#name-parameter-in-the-claims-que */
    @SerialName("intent_to_retain")
    val intentToRetain: Boolean? = null
) {
    init {
        require(path.isNotEmpty()) { "Claims Query path must not be empty" }
        require(path.all { segment ->
            when (segment) {
                JsonNull -> true
                is JsonPrimitive -> segment.isString || segment.content.toIntOrNull()?.let { it >= 0 } == true
                else -> false
            }
        }) {
            "Claims Query path elements must be strings, non-negative integers, or null wildcards"
        }
    }
    constructor(
        id: String? = null,
        pathStrings: List<String>,
        values: List<JsonPrimitive>? = null
    ) : this(
        id = id,
        path = pathStrings.map { JsonPrimitive(it) },
        values = values
    )
}
