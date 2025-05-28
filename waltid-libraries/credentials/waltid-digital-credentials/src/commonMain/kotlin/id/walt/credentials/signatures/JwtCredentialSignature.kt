package id.walt.credentials.signatures

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("signature-jwt")
data class JwtCredentialSignature(
    val signature: String
) : CredentialSignature() {

}
