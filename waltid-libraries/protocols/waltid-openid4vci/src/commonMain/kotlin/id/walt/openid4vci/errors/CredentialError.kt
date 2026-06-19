package id.walt.openid4vci.errors

import kotlinx.serialization.Serializable

@Serializable
data class CredentialError(
    val error: String,
    val description: String? = null,
)

/**
 * OpenID4VCI credential endpoint error codes.
 */
object CredentialErrorCodes {
    const val INVALID_REQUEST = "invalid_request"
    const val INVALID_TOKEN = "invalid_token"
    const val INSUFFICIENT_SCOPE = "insufficient_scope"
    const val UNSUPPORTED_CREDENTIAL_TYPE = "unsupported_credential_type"
    const val UNSUPPORTED_CREDENTIAL_FORMAT = "unsupported_credential_format"
    const val INVALID_OR_MISSING_PROOF = "invalid_or_missing_proof"
    const val SERVER_ERROR = "server_error"
}