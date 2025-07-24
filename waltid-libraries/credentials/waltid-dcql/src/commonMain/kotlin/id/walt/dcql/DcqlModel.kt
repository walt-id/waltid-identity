package id.walt.dcql

//import kotlinx.serialization.SerialName
//import kotlinx.serialization.Serializable
//import kotlinx.serialization.json.JsonObject
//import kotlinx.serialization.json.JsonPrimitive
//
///**
// * Represents the top-level DCQL query structure.
// * See: Section 6
// */
//@Serializable
//data class DcqlQuery(
//    val credentials: List<CredentialQuery>,
//    @SerialName("credential_sets")
//    val credentialSets: List<CredentialSetQuery>? = null,
//)
//
///**
// * Represents a query for a specific credential type.
// * See: Section 6.1
// */
//@Serializable
//data class CredentialQuery(
//    val id: String, // Identifier for this query within the request
//    val format: String, // Credential format identifier (e.g., "jwt_vc_json", "mso_mdoc")
//    val multiple: Boolean = false, // Can multiple credentials match this query?
//    val meta: JsonObject? = null, // Format-specific metadata constraints (simplified)
//    @SerialName("trusted_authorities")
//    val trustedAuthorities: List<TrustedAuthoritiesQuery>? = null, // Issuer constraints (simplified)
//    @SerialName("require_cryptographic_holder_binding")
//    val requireCryptographicHolderBinding: Boolean = true, // Holder binding requirement
//    val claims: List<ClaimsQuery>? = null, // Specific claims requested
//    @SerialName("claim_sets")
//    val claimSets: List<List<String>>? = null, // Combinations of claim IDs
//)
//
///**
// * Represents constraints on trusted issuers or frameworks.
// * See: Section 6.1.1
// */
//@Serializable
//data class TrustedAuthoritiesQuery(
//    val type: String, // e.g., "aki", "etsi_tl", "openid_federation"
//    val values: List<String>,
//)
//
///**
// * Represents constraints on specific claims within a credential.
// * See: Section 6.3
// */
//@Serializable
//data class ClaimsQuery(
//    // Required if claim_sets is present in the parent CredentialQuery
//    val id: String? = null,
//    val path: List<String>, // Path to the claim (format-specific interpretation)
//    val values: List<JsonPrimitive>? = null, // Optional specific values to match
//)
//
///**
// * Represents constraints on combinations of credentials.
// * See: Section 6.2
// */
//@Serializable
//data class CredentialSetQuery(
//    // Each inner list contains CredentialQuery IDs that form one valid set
//    val options: List<List<String>>,
//    val required: Boolean = true, // Is satisfying this set mandatory?
//)
