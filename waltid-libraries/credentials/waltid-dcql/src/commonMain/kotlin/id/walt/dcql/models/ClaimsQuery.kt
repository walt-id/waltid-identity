package id.walt.dcql.models

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
)
