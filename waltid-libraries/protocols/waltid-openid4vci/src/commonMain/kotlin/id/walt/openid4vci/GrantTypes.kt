package id.walt.openid4vci

/**
 * Grant type wrapper with built-in values and support for custom extensions.
 */
const val GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code"
const val GRANT_TYPE_PRE_AUTHORIZED_CODE = "urn:ietf:params:oauth:grant-type:pre-authorized_code"

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

    override fun toString(): String = value
}
