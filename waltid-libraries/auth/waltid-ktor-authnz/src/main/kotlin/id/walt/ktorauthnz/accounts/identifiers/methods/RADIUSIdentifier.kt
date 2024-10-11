package id.walt.ktorauthnz.accounts.identifiers.methods

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class RADIUSIdentifier(val host: String, val name: String) : AccountIdentifier("radius") {
    override fun toDataString() = Json.encodeToString(this)

    companion object : AccountIdentifierFactory<RADIUSIdentifier>("radius") {
        override fun fromAccountIdentifierDataString(dataString: String) = Json.decodeFromString<RADIUSIdentifier>(dataString)
    }
}
