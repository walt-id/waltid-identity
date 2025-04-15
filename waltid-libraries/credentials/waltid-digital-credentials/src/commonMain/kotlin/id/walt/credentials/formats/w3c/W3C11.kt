package id.walt.credentials.formats

import id.walt.credentials.signatures.CredentialSignature
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
@SerialName("vc-w3c_1_1")
data class W3C11(
    override val disclosables: Map<String, Set<String>>? = null,
    override val disclosures: List<SdJwtSelectiveDisclosure>? = null,
    override val signature: CredentialSignature?,
    override val signed: String?,
    override val signedWithDisclosures: String?,
    override val credentialData: JsonObject,
    override val originalCredentialData: JsonObject? = null
) : AbstractW3C() {

}
