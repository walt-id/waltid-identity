package id.walt.credentials.formats.sdjwtvc

import id.walt.credentials.CredentialDetectorTypes
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.credentials.formats.VerifiableCredential
import id.walt.credentials.signatures.CredentialSignature
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

data class SdJwtCredential(
    val type: CredentialDetectorTypes.SDJWTVCSubType,
    override val disclosableAttributes: JsonArray?,
    override val disclosuresString: String?,
    override val signature: CredentialSignature?,
    override val signed: String?, override val credentialData: JsonObject,
) : VerifiableCredential(), SelectivelyDisclosableVerifiableCredential {

}
