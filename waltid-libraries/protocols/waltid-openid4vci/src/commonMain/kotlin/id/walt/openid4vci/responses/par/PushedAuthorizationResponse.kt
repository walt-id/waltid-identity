package id.walt.openid4vci.responses.par

import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.requests.authorization.AuthorizationRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Pushed Authorization Response as per RFC 9126 §2.2
 *
 * Successful response from PAR endpoint containing a request_uri.
 */
@Serializable
data class PushedAuthorizationResponse(
    /**
     * The request URI corresponding to the authorization request posted.
     * This URI is a single-use reference to the respective request data.
     * RFC 9126 §2.2: MUST be included.
     */
    @SerialName("request_uri")
    val requestUri: String,

    /**
     * The lifetime in seconds of the request_uri.
     * RFC 9126 §2.2: MUST be included
     */
    @SerialName("expires_in")
    val expiresIn: Int,
) {
    init {
        require(requestUri.isNotBlank()) { "request_uri is required" }
        require(expiresIn > 0) { "expires_in must be positive" }
    }

    companion object {
        const val DEFAULT_REQUEST_URI_PREFIX = "urn:ietf:params:oauth:request_uri:"

        /**
         * Create a PAR response with a generated request_uri.
         *
         * The default prefix follows the registered OAuth URN format, but deployments may configure
         * another prefix as long as authorization request resolution uses the same value.
         *
         * @param requestId unique identifier for this PAR
         * @param expiresIn lifetime in seconds (default: 90s per RFC 9126 recommendation)
         */
        fun create(
            requestId: String,
            expiresIn: Int = 90,
            requestUriPrefix: String = DEFAULT_REQUEST_URI_PREFIX,
        ): PushedAuthorizationResponse {
            require(requestId.isNotBlank()) { "requestId must not be blank" }
            require(requestUriPrefix.isNotBlank()) { "requestUriPrefix must not be blank" }
            return PushedAuthorizationResponse(
                requestUri = "$requestUriPrefix$requestId",
                expiresIn = expiresIn
            )
        }

        /**
         * Extract the request ID from a request_uri
         */
        fun extractRequestId(
            requestUri: String,
            requestUriPrefix: String = DEFAULT_REQUEST_URI_PREFIX,
        ): String? {
            return if (requestUri.startsWith(requestUriPrefix)) {
                requestUri.removePrefix(requestUriPrefix).takeIf { it.isNotBlank() }
            } else {
                null
            }
        }
    }
}

sealed class PushedAuthorizationResponseResult {
    data class Success(
        val request: AuthorizationRequest,
        val response: PushedAuthorizationResponse,
    ) : PushedAuthorizationResponseResult()

    data class Failure(val error: OAuthError) : PushedAuthorizationResponseResult()

    fun isSuccess(): Boolean = this is Success
}

data class PushedAuthorizationResponseHttp(
    val status: Int,
    val payload: Map<String, JsonElement>,
    val headers: Map<String, String> = emptyMap(),
)