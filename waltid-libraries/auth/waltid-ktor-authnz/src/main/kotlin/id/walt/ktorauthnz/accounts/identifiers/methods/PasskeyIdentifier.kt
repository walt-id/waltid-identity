package id.walt.ktorauthnz.accounts.identifiers.methods

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A passkey (WebAuthn credential) of an account, by its credential id (base64url). */
@Serializable
@SerialName("passkey")
data class PasskeyIdentifier(
    val credentialId: String,
) : AccountIdentifier() {
    override fun identifierName() = "passkey"
    override fun toDataString() = credentialId

    companion object : AccountIdentifierFactory<PasskeyIdentifier>("passkey") {
        override fun fromAccountIdentifierDataString(dataString: String) = PasskeyIdentifier(dataString)

        val EXAMPLE = PasskeyIdentifier("q2d5ZXhhbXBsZQ")
    }
}
