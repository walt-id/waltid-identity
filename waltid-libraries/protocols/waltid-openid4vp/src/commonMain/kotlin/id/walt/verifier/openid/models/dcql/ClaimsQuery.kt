package id.walt.verifier.openid.models.dcql

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/**
 * Represents constraints on specific claims within a credential.
 * See: Section 6.3
 */
@Serializable
data class ClaimsQuery(
    val id: String? = null, // Required if claim_sets is present in parent CredentialQuery
    val path: List<String>,
    // kotlinx.serialization.json.JsonPrimitive can represent string, number, boolean
    val values: List<JsonPrimitive>? = null,
)
