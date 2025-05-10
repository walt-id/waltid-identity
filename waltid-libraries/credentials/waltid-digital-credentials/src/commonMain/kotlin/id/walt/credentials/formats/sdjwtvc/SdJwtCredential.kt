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
    val dmtype: CredentialDetectorTypes.SDJWTVCSubType,

    // Selective disclosure
    override val disclosables: Map<String, Set<String>>? = null,
    override val disclosures: List<SdJwtSelectiveDisclosure>? = null,
    override val signedWithDisclosures: String? = null,

    // Data
    override val credentialData: JsonObject,
    override val originalCredentialData: JsonObject? = null,

    override var issuer: String?,
    override var subject: String?,

    // Signature
    override val signature: CredentialSignature?,
    override val signed: String?,
) : DigitalCredential(), SelectivelyDisclosableVerifiableCredential {
    init {
        selfCheck()
    }
}
