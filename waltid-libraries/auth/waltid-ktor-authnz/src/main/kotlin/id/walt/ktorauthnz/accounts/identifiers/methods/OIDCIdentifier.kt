package id.walt.ktorauthnz.accounts.identifiers.methods

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
@SerialName("oidc")
data class OIDCIdentifier(val host: String, val name: String) : AccountIdentifier() {
    override fun identifierName() = "oidc"
    override fun toDataString() = Json.encodeToString(this)

    companion object : AccountIdentifierFactory<OIDCIdentifier>("oidc") {
        override fun fromAccountIdentifierDataString(dataString: String) = Json.decodeFromString<OIDCIdentifier>(dataString)
    }
}
