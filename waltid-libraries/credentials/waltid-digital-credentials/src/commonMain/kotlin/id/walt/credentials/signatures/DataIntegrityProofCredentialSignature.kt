package id.walt.credentials.signatures

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class DataIntegrityProofCredentialSignature(
    val signature: JsonObject
) : CredentialSignature() {
}
