package id.walt.openid4vci.responses.credential

import kotlinx.serialization.Serializable

/**
 * Minimal representation of a credential_configuration_supported entry.
 * Extend as needed to cover additional metadata fields.
 */
@Serializable
data class CredentialConfiguration(
    val id: String,
    val format: String,
)
