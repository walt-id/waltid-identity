package id.walt.credentials.formats

import id.walt.credentials.CredentialDetectorTypes
import id.walt.credentials.signatures.CredentialSignature
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

@Serializable
data class SdJwtCredential(
    val type: CredentialDetectorTypes.SDJWTVCSubType,
    override val disclosableAttributes: JsonArray?,
    override val disclosuresString: String?,
    override val signature: CredentialSignature?,
    override val signed: String?, override val credentialData: JsonObject,
) : DigitalCredential(), SelectivelyDisclosableVerifiableCredential {

}
