package id.walt.openid4vci


import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * OAuth 2.0 (RFC 6749) grant types and OID4VCI extensions.
 * Wrapper that contains built-in values and supports custom extensions.
 */
@Serializable
sealed class GrantType {
    abstract val value: String

    @Serializable
    @SerialName("authorization_code")
    object AuthorizationCode : GrantType() {
        override val value: String get() = GRANT_TYPE_AUTHORIZATION_CODE
    }

    @Serializable
    @SerialName("pre-authorized_code")
    object PreAuthorizedCode : GrantType() {
        override val value: String get() = GRANT_TYPE_PRE_AUTHORIZED_CODE
    }

    @Serializable
    @SerialName("custom")
    data class Custom(override val value: String) : GrantType()

    companion object {
        fun fromValue(raw: String): GrantType = when (raw.lowercase()) {
            GRANT_TYPE_AUTHORIZATION_CODE -> AuthorizationCode
            GRANT_TYPE_PRE_AUTHORIZED_CODE -> PreAuthorizedCode
            else -> Custom(raw.lowercase())
        }
    }

}

private const val GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code"
private const val GRANT_TYPE_PRE_AUTHORIZED_CODE = "urn:ietf:params:oauth:grant-type:pre-authorized_code"
