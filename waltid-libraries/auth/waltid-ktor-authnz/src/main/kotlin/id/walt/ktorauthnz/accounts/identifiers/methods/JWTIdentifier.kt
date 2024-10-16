package id.walt.ktorauthnz.accounts.identifiers.methods

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("jwt")
data class JWTIdentifier(val subject: String) : AccountIdentifier() {
    override fun identifierName() = "jwt"
    override fun toDataString() = subject

    companion object : AccountIdentifierFactory<JWTIdentifier>("jwt") {
        override fun fromAccountIdentifierDataString(dataString: String) = JWTIdentifier(dataString)
    }
}
