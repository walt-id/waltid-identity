package id.walt.ktorauthnz.methods.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("ldap-config")
data class LDAPConfiguration(
    val ldapServerUrl: String,
    val userDNFormat: String,
) : AuthMethodConfiguration
