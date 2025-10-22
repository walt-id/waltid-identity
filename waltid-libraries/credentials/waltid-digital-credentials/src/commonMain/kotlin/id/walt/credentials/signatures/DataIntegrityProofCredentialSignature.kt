package id.walt.credentials.signatures

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
@SerialName("signature-dip")
data class DataIntegrityProofCredentialSignature(
    val signature: JsonObject,
    //override var signerKey: Key,
) : CredentialSignature() {
}
