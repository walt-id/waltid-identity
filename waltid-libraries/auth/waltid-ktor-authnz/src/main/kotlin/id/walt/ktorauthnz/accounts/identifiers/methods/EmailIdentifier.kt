package id.walt.ktorauthnz.accounts.identifiers.methods

import kotlinx.serialization.Serializable

@Serializable
data class EmailIdentifier(val email: String) : AccountIdentifier("email") {
    override fun toDataString() = email

    companion object : AccountIdentifierFactory<EmailIdentifier>("email") {
        override fun fromAccountIdentifierDataString(dataString: String): EmailIdentifier = EmailIdentifier(dataString)
    }
}
