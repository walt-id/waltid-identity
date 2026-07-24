package id.waltid.openid4vci.wallet.authorization

import id.waltid.openid4vci.wallet.oauth.StateManager
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*

private val log = KotlinLogging.logger {}

/**
 * Parses OAuth 2.0 authorization responses.
 * Implements authorization response handling as per RFC 6749.
 */
object AuthorizationResponseParser {

    /**
     * Represents a successful authorization response
     */
    data class AuthorizationResponse(
        val code: String,
        val state: String,
    )

    /**
     * Represents an authorization error response
     */
    data class AuthorizationError(
        val error: String,
        val errorDescription: String? = null,
        val errorUri: String? = null,
        val state: String? = null,
    )

    /**
     * Parses an authorization response from a redirect URI
     * 
     * @param redirectUri The full redirect URI with query parameters or fragment
     * @param expectedState The expected state value for CSRF validation
     * @return AuthorizationResponse on success
     * @throws IllegalArgumentException on parsing errors or state mismatch
     * @throws AuthorizationErrorException if the response contains an error
     */
    fun parseAuthorizationResponse(
        redirectUri: String,
        expectedState: String,
    ): AuthorizationResponse {
        require(redirectUri.isNotBlank()) { "Redirect URI cannot be blank" }
        require(expectedState.isNotBlank()) { "Expected state cannot be blank" }

        val url = try {
            Url(redirectUri)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid redirect URI: $redirectUri", e)
        }

        // Authorization responses can be in query parameters or fragment
        val parameters = if (url.fragment.isNotBlank()) {
            parseFragmentParameters(url.fragment)
        } else {
            url.parameters
        }

        // Check for error response first
        val error = parameters["error"]
        if (error != null) {
            val authError = AuthorizationError(
                error = error,
                errorDescription = parameters["error_description"],
                errorUri = parameters["error_uri"],
                state = parameters["state"]
            )
            log.error { "Authorization error response: $authError" }
            throw AuthorizationErrorException(authError)
        }

        // Extract code and state
        val code = parameters["code"]
            ?: throw IllegalArgumentException("Authorization response missing 'code' parameter")

        val state = parameters["state"]
            ?: throw IllegalArgumentException("Authorization response missing 'state' parameter")

        // Validate state
        if (!StateManager.validateState(expectedState, state)) {
            log.error { "State mismatch: expected=$expectedState, actual=$state" }
            throw IllegalArgumentException("State validation failed - possible CSRF attack")
        }

        log.debug { "Successfully parsed authorization response" }
        return AuthorizationResponse(code = code, state = state)
    }

    /**
     * Parses query parameters from a URL fragment
     */
    private fun parseFragmentParameters(fragment: String): Parameters {
        val params = ParametersBuilder()
        fragment.split("&").forEach { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                params.append(parts[0], parts[1].decodeURLPart())
            }
        }
        return params.build()
    }

    /**
     * Exception thrown when authorization response contains an error
     */
    class AuthorizationErrorException(val authError: AuthorizationError) :
        Exception("Authorization failed: ${authError.error} - ${authError.errorDescription ?: "No description"}")
}
