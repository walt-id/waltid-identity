package id.walt.ktorauthnz.accounts.identifiers.methods

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
@SerialName("oidc")
data class OIDCIdentifier(
    /** The issuer URL of the OpenID Provider, e.g., "https://idp.example.com/realms/my-realm" */
    val issuer: String,
    /** The subject ('sub') claim for the user from the IdP */
    val subject: String
) : AccountIdentifier() {
    override fun identifierName() = "oidc"
    override fun toDataString() = Json.encodeToString(this)

    companion object : AccountIdentifierFactory<OIDCIdentifier>("oidc") {
        override fun fromAccountIdentifierDataString(dataString: String) =
            Json.decodeFromString<OIDCIdentifier>(dataString)

        val EXAMPLE = OIDCIdentifier("issuer", "subject")
    }
}
