package id.walt.ktorauthnz.methods.config

import id.walt.ktorauthnz.methods.LDAP
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("ldap-config")
data class LDAPConfiguration(
    val ldapServerUrl: String,
    val userDNFormat: String,
) : AuthMethodConfiguration {
    override fun authMethod() = LDAP
}
