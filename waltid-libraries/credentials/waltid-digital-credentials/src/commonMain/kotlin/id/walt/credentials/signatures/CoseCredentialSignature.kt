package id.walt.credentials.signatures

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// TODO
@Serializable
@SerialName("signature-cose")
data class CoseCredentialSignature(
    val x: String? = null // What does the COSE signature have/need?
) : CredentialSignature() {
}
