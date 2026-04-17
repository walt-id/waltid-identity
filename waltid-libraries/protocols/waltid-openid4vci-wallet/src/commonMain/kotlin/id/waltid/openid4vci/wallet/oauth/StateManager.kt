package id.waltid.openid4vci.wallet.oauth

import kotlin.random.Random

/**
 * Manages OAuth 2.0 state parameter for CSRF protection.
 * Implements RFC 6749 Section 10.12 (Cross-Site Request Forgery).
 */
object StateManager {

    /**
     * Generates a cryptographically random state parameter
     * 
     * @param length Length of the state string (default: 32 characters)
     * @return Random state string
     */
    fun generateState(length: Int = 32): String {
        require(length > 0) { "State length must be positive" }

        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('-', '_')
        return (1..length)
            .map { allowedChars[Random.nextInt(allowedChars.size)] }
            .joinToString("")
    }

    /**
     * Validates that a state parameter matches the expected value
     * 
     * @param expected The expected state value
     * @param actual The actual state value received
     * @return true if states match, false otherwise
     */
    fun validateState(expected: String, actual: String): Boolean =
        expected.isNotBlank() && expected == actual
}
