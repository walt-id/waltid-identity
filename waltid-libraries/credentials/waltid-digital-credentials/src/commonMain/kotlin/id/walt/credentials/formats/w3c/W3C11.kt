package id.walt.credentials.formats

import id.walt.credentials.signatures.CredentialSignature
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

@Serializable
data class W3C11(
    override val disclosableAttributes: JsonArray?,
    override val disclosuresString: String?,
    override val signature: CredentialSignature?,
    override val signed: String?,
    override val credentialData: JsonObject
) : AbstractW3C() {

}
