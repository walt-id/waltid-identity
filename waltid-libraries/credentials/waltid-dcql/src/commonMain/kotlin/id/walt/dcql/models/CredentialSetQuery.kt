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
) {
    init {
        require(options.isNotEmpty()) { "Credential set options must not be empty" }
        require(options.all { it.isNotEmpty() }) { "Credential set alternatives must not be empty" }
        require(options.all { it.distinct().size == it.size }) {
            "Credential set alternatives must not contain duplicate credential IDs"
        }
        require(options.map { it.toSet() }.distinct().size == options.size) {
            "Credential set alternatives must not be duplicated independent of order"
        }
    }
}
