package id.walt.openid4vci

/**
 * Grant type wrapper with built-in values and support for custom extensions.
 */
sealed class GrantType(open val value: String) {
    object AuthorizationCode : GrantType("authorization_code")
    object PreAuthorizedCode : GrantType("urn:ietf:params:oauth:grant-type:pre-authorized_code")
    data class Custom(override val value: String) : GrantType(value)

    companion object {
        fun fromValue(raw: String): GrantType = when (raw.lowercase()) {
            AuthorizationCode.value -> AuthorizationCode
            PreAuthorizedCode.value -> PreAuthorizedCode
            else -> Custom(raw.lowercase())
        }
    }

    override fun toString(): String = value
}
