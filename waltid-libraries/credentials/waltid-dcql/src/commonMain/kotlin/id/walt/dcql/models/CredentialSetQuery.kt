package id.walt.dcql.models

import kotlinx.serialization.Serializable

/**
 * Represents constraints on combinations of credentials.
 * See: Section 6.2
 */
@Serializable
data class CredentialSetQuery(
    /** Each inner list contains CredentialQuery IDs that form one valid set */
    val options: List<List<String>>,

    /** Is satisfying this set mandatory? */
    val required: Boolean = true,
)
