package id.walt.openid4vci.responses.par

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
     * RFC 9126 §2.2: MUST be included, format urn:ietf:params:oauth:request_uri:<reference-value>
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
        require(requestUri.startsWith("urn:ietf:params:oauth:request_uri:")) {
            "request_uri must follow RFC 9126 format: urn:ietf:params:oauth:request_uri:<reference>"
        }
        require(expiresIn > 0) { "expires_in must be positive" }
    }

    companion object {
        private const val REQUEST_URI_PREFIX = "urn:ietf:params:oauth:request_uri:"

        /**
         * Create a PAR response with a generated request_uri
         * @param requestId unique identifier for this PAR
         * @param expiresIn lifetime in seconds (default: 90s per RFC 9126 recommendation)
         */
        fun create(requestId: String, expiresIn: Int = 90): PushedAuthorizationResponse {
            require(requestId.isNotBlank()) { "requestId must not be blank" }
            return PushedAuthorizationResponse(
                requestUri = "$REQUEST_URI_PREFIX$requestId",
                expiresIn = expiresIn
            )
        }

        /**
         * Extract the request ID from a request_uri
         */
        fun extractRequestId(requestUri: String): String? {
            return if (requestUri.startsWith(REQUEST_URI_PREFIX)) {
                requestUri.removePrefix(REQUEST_URI_PREFIX)
            } else {
                null
            }
        }
    }
}
