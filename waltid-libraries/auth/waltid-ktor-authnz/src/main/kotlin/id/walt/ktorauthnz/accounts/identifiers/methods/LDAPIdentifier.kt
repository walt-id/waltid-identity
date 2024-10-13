package id.walt.ktorauthnz.accounts.identifiers.methods

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
@SerialName("ldap")
data class LDAPIdentifier(val host: String, val name: String) : AccountIdentifier() {
    override fun identifierName() = "ldap"
    override fun toDataString() = Json.encodeToString(this)

    companion object : AccountIdentifierFactory<LDAPIdentifier>("ldap") {
        override fun fromAccountIdentifierDataString(dataString: String) = Json.decodeFromString<LDAPIdentifier>(dataString)
    }
}
