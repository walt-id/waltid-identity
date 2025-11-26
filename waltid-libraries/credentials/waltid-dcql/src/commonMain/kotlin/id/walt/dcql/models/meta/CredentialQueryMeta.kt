package id.walt.dcql.models.meta

import id.walt.dcql.models.CredentialFormat
import kotlinx.serialization.Serializable

/**
 * Sealed interface for format-specific metadata within a CredentialQuery.
 */
@Serializable
sealed interface CredentialQueryMeta {
    /** What CredentialFormat is this CredentialQueryMeta tied to */
    val format: CredentialFormat?
    fun getTypeString(): String?
}

