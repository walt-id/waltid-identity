package id.walt.dcql.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 * Represents constraints on specific claims within a credential.
 * See: Section 6.3
 */
@Serializable
data class ClaimsQuery(
    /** Required if claim_sets is present in parent CredentialQuery */
    val id: String? = null,

    /** Path to the claim (format-specific interpretation) */
    val path: List<String>,

    /** Optional specific values to match */
    val values: List<JsonPrimitive>? = null,

    /** defined at https://openid.net/specs/openid-4-verifiable-presentations-1_0.html#name-parameter-in-the-claims-que */
    @SerialName("intent_to_retain")
    val intentToRetain: Boolean? = null
)
