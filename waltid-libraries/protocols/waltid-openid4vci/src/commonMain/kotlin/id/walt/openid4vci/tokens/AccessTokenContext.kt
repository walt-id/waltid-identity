package id.walt.openid4vci.tokens

/**
 * Context for access token verification.
 * The caller supplies the raw token plus trusted expectations.
 */
data class AccessTokenContext(
    val token: String,
    val expectedIssuer: String,
    val expectedAudience: String? = null,
)
