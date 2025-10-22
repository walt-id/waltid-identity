package id.walt.credentials.signatures

import id.walt.credentials.representations.X5CList
import id.walt.crypto.keys.DirectSerializedKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("signature-cose")
data class CoseCredentialSignature(
    /*override*/ val signerKey: DirectSerializedKey,
    val x5cList: X5CList? = null,
) : CredentialSignature() {
}
