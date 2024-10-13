package id.walt.ktorauthnz.accounts.identifiers.methods

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("email")
data class EmailIdentifier(val email: String) : AccountIdentifier() {
    override fun identifierName() = "email"

    override fun toDataString() = email

    companion object : AccountIdentifierFactory<EmailIdentifier>("email") {
        override fun fromAccountIdentifierDataString(dataString: String): EmailIdentifier = EmailIdentifier(dataString)
    }


}
