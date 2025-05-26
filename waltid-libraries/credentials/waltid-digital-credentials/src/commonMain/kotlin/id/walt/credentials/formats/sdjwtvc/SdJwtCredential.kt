package id.walt.credentials.formats

import id.walt.credentials.CredentialDetectorTypes
import id.walt.credentials.signatures.CredentialSignature
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@OptIn(ExperimentalSerializationApi::class)
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

    @EncodeDefault
    override var issuer: String? = null,
    @EncodeDefault
    override var subject: String? = null,

    // Signature
    override val signature: CredentialSignature?,
    override val signed: String?,
) : DigitalCredential(), SelectivelyDisclosableVerifiableCredential {
    override val format: String = "dc+sd-jwt"

    init {
        selfCheck()
    }
}
