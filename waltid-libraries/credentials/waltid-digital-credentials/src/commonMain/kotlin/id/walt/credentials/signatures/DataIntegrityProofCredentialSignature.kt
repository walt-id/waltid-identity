package id.walt.credentials.signatures

import kotlinx.serialization.json.JsonObject

class DataIntegrityProofCredentialSignature(
    val signature: JsonObject
) : CredentialSignature() {
}
