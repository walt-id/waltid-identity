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
) {

    fun precheck() {
        if (credentials.isEmpty()) {
            throw IllegalArgumentException("Requested dcql query: credentials cannot be empty")
        }

        if (credentials.any { it.id.isEmpty() }) {
            throw IllegalArgumentException("Requested dcql query: credential has empty id")
        }

        /* // See OSS #1270
        if (credentials.any { it.meta == NoMeta }) {
            throw IllegalArgumentException("Requested dcql query: credential has no meta field")
        }*/

        if (credentials.any { it.claims != null && it.claims.isEmpty() }) {
            throw IllegalArgumentException("Requested dcql query: claims was set, but has no elements")
        }
    }

}
