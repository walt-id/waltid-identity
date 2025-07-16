package id.walt.dcql.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the top-level DCQL query structure.
 * See: Section 6 of OpenID4VP spec
 */
@Serializable
data class DcqlQuery(
    val credentials: List<CredentialQuery>,
    @SerialName("credential_sets")
    val credentialSets: List<CredentialSetQuery>? = null,
)
