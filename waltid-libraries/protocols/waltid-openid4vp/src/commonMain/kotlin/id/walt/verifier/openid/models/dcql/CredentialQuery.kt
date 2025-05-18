package id.walt.verifier.openid.models.dcql

import id.walt.verifier.openid.models.credentials.CredentialFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Represents a query for a specific credential type.
 * See: Section 6.1
 */
@Serializable
data class CredentialQuery(
    val id: String,
    val format: CredentialFormat,
    val multiple: Boolean = false,
    val meta: JsonObject? = null,
    @SerialName("trusted_authorities")
    val trustedAuthorities: List<TrustedAuthoritiesQuery>? = null,
    @SerialName("require_cryptographic_holder_binding")
    val requireCryptographicHolderBinding: Boolean = true,
    val claims: List<ClaimsQuery>? = null,
    @SerialName("claim_sets")
    val claimSets: List<List<String>>? = null,
)
