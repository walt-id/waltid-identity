package id.walt.ktorauthnz.accounts.identifiers.methods

import kotlinx.serialization.Serializable

@Serializable
data class UsernameIdentifier(val name: String) : AccountIdentifier("username") {
    override fun toDataString() = name

    companion object : AccountIdentifierFactory<UsernameIdentifier>("username") {
        override fun fromAccountIdentifierDataString(dataString: String) = UsernameIdentifier(dataString)
    }
}
