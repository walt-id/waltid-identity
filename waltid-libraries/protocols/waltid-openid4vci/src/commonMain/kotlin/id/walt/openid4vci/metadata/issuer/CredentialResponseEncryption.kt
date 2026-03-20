package id.walt.openid4vci.metadata.issuer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Credential Response encryption metadata (OpenID4VCI 1.0).
 */
@Serializable
data class CredentialResponseEncryption(
    @SerialName("alg_values_supported")
    val algValuesSupported: Set<String>,
    @SerialName("enc_values_supported")
    val encValuesSupported: Set<String>,
    @SerialName("zip_values_supported")
    val zipValuesSupported: Set<String>? = null,
    @SerialName("encryption_required")
    val encryptionRequired: Boolean,
) {
    init {
        require(algValuesSupported.isNotEmpty()) {
            "credential_response_encryption.alg_values_supported must not be empty"
        }
        require(algValuesSupported.none { it.isBlank() }) {
            "credential_response_encryption.alg_values_supported must not contain blank entries"
        }
        require(encValuesSupported.isNotEmpty()) {
            "credential_response_encryption.enc_values_supported must not be empty"
        }
        require(encValuesSupported.none { it.isBlank() }) {
            "credential_response_encryption.enc_values_supported must not contain blank entries"
        }
        zipValuesSupported?.let { values ->
            require(values.isNotEmpty()) {
                "credential_response_encryption.zip_values_supported must not be empty"
            }
            require(values.none { it.isBlank() }) {
                "credential_response_encryption.zip_values_supported must not contain blank entries"
            }
        }
    }
}
