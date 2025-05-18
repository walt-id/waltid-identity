package id.walt.verifier.openid.models.dcql

import kotlinx.serialization.Serializable

/**
 * Represents constraints on combinations of credentials.
 * See: Section 6.2
 */
@Serializable
data class CredentialSetQuery(
    val options: List<List<String>>,
    val required: Boolean = true,
)
