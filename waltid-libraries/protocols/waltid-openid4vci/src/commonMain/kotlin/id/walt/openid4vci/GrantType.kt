package id.walt.openid4vci


/**
 * OAuth 2.0 (RFC 6749) grant types and OID4VCI extensions.
 * Wrapper that contains built-in values and supports custom extensions.
 */
sealed class GrantType(open val value: String) {
    object AuthorizationCode : GrantType(GRANT_TYPE_AUTHORIZATION_CODE)
    object PreAuthorizedCode : GrantType(GRANT_TYPE_PRE_AUTHORIZED_CODE)
    data class Custom(override val value: String) : GrantType(value)

    companion object {
        fun fromValue(raw: String): GrantType = when (raw.lowercase()) {
            AuthorizationCode.value -> AuthorizationCode
            PreAuthorizedCode.value -> PreAuthorizedCode
            else -> Custom(raw.lowercase())
        }
    }

}

private const val GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code"
private const val GRANT_TYPE_PRE_AUTHORIZED_CODE = "urn:ietf:params:oauth:grant-type:pre-authorized_code"
