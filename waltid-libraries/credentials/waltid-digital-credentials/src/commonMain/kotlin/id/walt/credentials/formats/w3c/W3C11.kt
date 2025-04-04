package id.walt.credentials.formats.w3c

import id.walt.credentials.signatures.CredentialSignature
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

data class W3C11(
    override val disclosableAttributes: JsonArray?,
    override val disclosuresString: String?,
    override val signature: CredentialSignature?,
    override val signed: String?,
    override val credentialData: JsonObject
) : AbstractW3C() {

}
