package id.walt.ktorauthnz.accounts.identifiers.methods

import kotlinx.serialization.Serializable

@Serializable
data class JWTIdentifier(val subject: String) : AccountIdentifier("jwt") {
    override fun toDataString() = subject

    companion object : AccountIdentifierFactory<JWTIdentifier>("jwt") {
        override fun fromAccountIdentifierDataString(dataString: String) = JWTIdentifier(dataString)
    }
}
