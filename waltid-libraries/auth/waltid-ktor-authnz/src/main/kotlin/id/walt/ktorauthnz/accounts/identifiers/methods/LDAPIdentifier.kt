package id.walt.ktorauthnz.accounts.identifiers.methods

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class LDAPIdentifier(val host: String, val name: String) : AccountIdentifier("ldap") {
    override fun toDataString() = Json.encodeToString(this)

    companion object : AccountIdentifierFactory<LDAPIdentifier>("ldap") {
        override fun fromAccountIdentifierDataString(dataString: String) = Json.decodeFromString<LDAPIdentifier>(dataString)
    }
}
