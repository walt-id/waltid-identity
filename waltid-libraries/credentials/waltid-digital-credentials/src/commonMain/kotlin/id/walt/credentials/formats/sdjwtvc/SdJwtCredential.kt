package id.walt.credentials.formats

import id.walt.credentials.CredentialDetectorTypes
import id.walt.credentials.signatures.CredentialSignature
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
@SerialName("vc-sd_jwt")
data class SdJwtCredential(
    val type: CredentialDetectorTypes.SDJWTVCSubType,
    override val disclosables: Map<String, Set<String>>?,
    override val disclosures: List<SdJwtSelectiveDisclosure>?,
    override val signature: CredentialSignature?,
    override val signed: String?, override val credentialData: JsonObject,
) : DigitalCredential(), SelectivelyDisclosableVerifiableCredential {

}
