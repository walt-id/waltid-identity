package id.walt.credentials.signatures

import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("signature-sd_jwt")
data class SdJwtCredentialSignature(
    val signature: String,
    val providedDisclosures: List<SdJwtSelectiveDisclosure>? = null
) : CredentialSignature() {
}
