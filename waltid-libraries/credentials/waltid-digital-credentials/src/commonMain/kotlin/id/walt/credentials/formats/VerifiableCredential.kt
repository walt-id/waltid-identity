package id.walt.credentials.formats

import id.walt.credentials.signatures.CredentialSignature
import kotlinx.serialization.json.JsonObject

abstract class VerifiableCredential {
    abstract val credentialData: JsonObject
    abstract val signature: CredentialSignature?
    abstract val signed: String?

    //fun sign()
    // also see `fun signAndDisclose` from `SelectivelyDisclosable.kt`

    //fun verify()
}
