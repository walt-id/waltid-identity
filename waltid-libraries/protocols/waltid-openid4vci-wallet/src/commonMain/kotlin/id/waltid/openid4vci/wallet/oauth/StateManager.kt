package id.waltid.openid4vci.wallet.oauth

import kotlin.uuid.Uuid

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

        return buildString {
            while (this.length < length) {
                append(Uuid.random().toString().replace("-", ""))
            }
        }.take(length)
    }

    /**
     * Validates that a state parameter matches the expected value
     * 
     * @param expected The expected state value
     * @param actual The actual state value received
     * @return true if states match, false otherwise
     */
    fun validateState(expected: String, actual: String): Boolean =
        expected.isNotBlank() && constantTimeEquals(expected, actual)

    private fun constantTimeEquals(expected: String, actual: String): Boolean {
        if (expected.length != actual.length) return false
        var difference = 0
        expected.indices.forEach { index ->
            difference = difference or (expected[index].code xor actual[index].code)
        }
        return difference == 0
    }
}
