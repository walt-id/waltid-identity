package id.walt.credentials.formats

import id.walt.credentials.signatures.CredentialSignature
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
sealed class DigitalCredential {
    abstract val credentialData: JsonObject
    abstract val signature: CredentialSignature?
    abstract val signed: String?

    abstract var issuer: String?
    abstract var subject: String?

    //fun sign()
    // also see `fun signAndDisclose` from `SelectivelyDisclosable.kt`

    //fun verify()
}
