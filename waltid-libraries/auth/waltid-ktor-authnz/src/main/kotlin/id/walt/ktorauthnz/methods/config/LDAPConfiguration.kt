package id.walt.ktorauthnz.methods.config

import id.walt.ktorauthnz.methods.LDAP
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("ldap-config")
data class LDAPConfiguration(
    /** `ldap://host[:port]` or `ldaps://host[:port]` (TLS from the start); the ports default to 389 and 636. */
    val ldapServerUrl: String,
    /** The DN to bind as, with `%s` for the login name (escaped as a DN value), e.g. `uid=%s,ou=people,dc=example,dc=com`. */
    val userDNFormat: String,
    /** Upgrade an `ldap://` connection with StartTLS before binding, so that the password is not sent in the clear. */
    val startTls: Boolean = false,
) : AuthMethodConfiguration {
    override fun authMethod() = LDAP
}
