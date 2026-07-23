package id.walt.openid4vci.errors

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CredentialError(
    val error: String,
    @SerialName("error_description")
    val description: String? = null,
)

/**
 * OpenID4VCI credential endpoint error codes.
 */
object CredentialErrorCodes {
    const val INVALID_CREDENTIAL_REQUEST = "invalid_credential_request"
    const val UNKNOWN_CREDENTIAL_CONFIGURATION = "unknown_credential_configuration"
    const val UNKNOWN_CREDENTIAL_IDENTIFIER = "unknown_credential_identifier"
    const val INVALID_PROOF = "invalid_proof"
    const val INVALID_NONCE = "invalid_nonce"
    const val INVALID_ENCRYPTION_PARAMETERS = "invalid_encryption_parameters"
    const val CREDENTIAL_REQUEST_DENIED = "credential_request_denied"
}
