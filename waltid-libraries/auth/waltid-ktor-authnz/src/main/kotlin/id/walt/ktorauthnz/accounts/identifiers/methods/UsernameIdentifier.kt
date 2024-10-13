package id.walt.ktorauthnz.accounts.identifiers.methods

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("userpass")
data class UsernameIdentifier(val name: String) : AccountIdentifier() {
    override fun identifierName() = "userpass"
    override fun toDataString() = name

    companion object : AccountIdentifierFactory<UsernameIdentifier>("userpass") {
        override fun fromAccountIdentifierDataString(dataString: String) = UsernameIdentifier(dataString)
    }
}
