package id.walt.ktorauthnz.accounts.identifiers.methods

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** An account found by a claim of a presented credential, e.g. `credentialSubject.id` or an email address. */
@Serializable
@SerialName("vc")
data class VerifiableCredentialIdentifier(
    /** The claim path, joined with `/`, e.g. `credentialSubject/id`. */
    val claim: String,
    val value: String,
) : AccountIdentifier() {
    override fun identifierName() = "vc"
    override fun toDataString() = Json.encodeToString(this)

    companion object : AccountIdentifierFactory<VerifiableCredentialIdentifier>("vc") {
        override fun fromAccountIdentifierDataString(dataString: String) =
            Json.decodeFromString<VerifiableCredentialIdentifier>(dataString)

        val EXAMPLE = VerifiableCredentialIdentifier("credentialSubject/id", "did:key:z6Mk...")
    }
}
