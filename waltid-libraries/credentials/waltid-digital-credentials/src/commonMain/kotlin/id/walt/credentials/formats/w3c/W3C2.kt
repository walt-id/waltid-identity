package id.walt.credentials.formats

import id.walt.credentials.signatures.CredentialSignature
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
@SerialName("vc-w3c_2")
data class W3C2(
    override val disclosables: Map<String, Set<String>>?,
    override val disclosures: List<SdJwtSelectiveDisclosure>?,
    override val signature: CredentialSignature?,
    override val signed: String?,
    override val credentialData: JsonObject
) : AbstractW3C() {

}
