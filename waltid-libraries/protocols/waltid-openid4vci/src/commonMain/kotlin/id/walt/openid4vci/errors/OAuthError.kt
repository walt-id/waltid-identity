package id.walt.openid4vci.errors

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OAuthError(
    val error: String,
    @SerialName("error_description")
    val description: String? = null,
)

/**
 * OAuth 2.0 RFC 6749 standard error codes.
 */
object OAuthErrorCodes {
    const val INVALID_REQUEST = "invalid_request"
    const val UNAUTHORIZED_CLIENT = "unauthorized_client"
    const val ACCESS_DENIED = "access_denied"
    const val UNSUPPORTED_RESPONSE_TYPE = "unsupported_response_type"
    const val INVALID_SCOPE = "invalid_scope"
    const val SERVER_ERROR = "server_error"
    const val TEMPORARILY_UNAVAILABLE = "temporarily_unavailable"
    const val INVALID_CLIENT = "invalid_client"
    const val INVALID_GRANT = "invalid_grant"
    const val UNSUPPORTED_GRANT_TYPE = "unsupported_grant_type"
    const val INVALID_REQUEST_URI = "invalid_request_uri"
    const val INVALID_TOKEN = "invalid_token"
    const val INSUFFICIENT_SCOPE = "insufficient_scope"
    const val INVALID_DPOP_PROOF = "invalid_dpop_proof"
}
