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
    ): AuthorizationResponse = parseAuthorizationResponse(
        redirectUri = redirectUri,
        expectedState = expectedState,
        expectedRedirectUri = null,
    )

    /**
     * Parses an authorization response and binds it to the registered redirect target.
     */
    fun parseAuthorizationResponse(
        redirectUri: String,
        expectedState: String,
        expectedRedirectUri: String?,
    ): AuthorizationResponse = parseAuthorizationResponse(
        redirectUri = redirectUri,
        expectedState = expectedState,
        expectedRedirectUri = expectedRedirectUri,
        expectedIssuer = null,
        requireIssuer = false,
    )

    /**
     * Parses an authorization response and validates RFC 9207 issuer identification when present.
     *
     * [expectedIssuer] is compared exactly after URI parsing; [requireIssuer] should reflect the
     * authorization server's `authorization_response_iss_parameter_supported` metadata.
     */
    fun parseAuthorizationResponse(
        redirectUri: String,
        expectedState: String,
        expectedRedirectUri: String?,
        expectedIssuer: String?,
        requireIssuer: Boolean,
    ): AuthorizationResponse {
        require(redirectUri.isNotBlank()) { "Redirect URI cannot be blank" }
        require(expectedState.isNotBlank()) { "Expected state cannot be blank" }

        val url = try {
            Url(redirectUri)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid authorization callback URI", e)
        }

        require(url.fragment.isBlank()) {
            "Authorization code callbacks must use query parameters"
        }
        expectedRedirectUri?.let { expected ->
            require(matchesRedirectTarget(expected, url)) {
                "Authorization callback redirect URI does not match the issuance session"
            }
        }

        val parameters = url.parameters
        listOf("code", "state", "error", "error_description", "error_uri", "iss").forEach { name ->
            require(parameters.getAll(name).orEmpty().size <= 1) {
                "Authorization callback contains duplicate '$name' parameters"
            }
        }

        val state = parameters["state"]
            ?: throw IllegalArgumentException("Authorization response missing 'state' parameter")
        if (!StateManager.validateState(expectedState, state)) {
            throw IllegalArgumentException("State validation failed")
        }

        val issuer = parameters["iss"]
        require(!requireIssuer || issuer != null) {
            "Authorization response missing required 'iss' parameter"
        }
        if (issuer != null) {
            val expected = requireNotNull(expectedIssuer) {
                "Authorization response contains 'iss' but no expected issuer is configured"
            }
            require(issuer == expected) {
                "Authorization response issuer does not match the issuance session"
            }
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
            throw AuthorizationErrorException(authError)
        }

        // Extract code and state
        val code = parameters["code"]
            ?: throw IllegalArgumentException("Authorization response missing 'code' parameter")

        log.debug { "Successfully parsed authorization response" }
        return AuthorizationResponse(code = code, state = state)
    }

    private fun matchesRedirectTarget(expectedRedirectUri: String, callback: Url): Boolean {
        val expected = try {
            Url(expectedRedirectUri)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid expected redirect URI", e)
        }
        if (expected.fragment.isNotBlank()) return false
        if (expected.protocol != callback.protocol ||
            !expected.host.equals(callback.host, ignoreCase = true) ||
            expected.port != callback.port ||
            expected.encodedPath != callback.encodedPath
        ) {
            return false
        }

        val responseParameters = setOf("code", "state", "error", "error_description", "error_uri", "iss")
        val callbackBaseNames = callback.parameters.names() - responseParameters
        if (callbackBaseNames != expected.parameters.names()) return false
        return callbackBaseNames.all { name ->
            callback.parameters.getAll(name) == expected.parameters.getAll(name)
        }
    }

    /**
     * Exception thrown when authorization response contains an error
     */
    class AuthorizationErrorException(val authError: AuthorizationError) :
        Exception("Authorization failed: ${authError.error}")
}
