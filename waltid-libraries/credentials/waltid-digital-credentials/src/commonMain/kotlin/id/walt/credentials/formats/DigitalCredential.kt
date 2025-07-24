package id.walt.credentials.formats

import id.walt.credentials.signatures.CredentialSignature
import id.walt.crypto.keys.Key
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
sealed class DigitalCredential {
    abstract val format: String

    abstract val credentialData: JsonObject
    abstract val signature: CredentialSignature?
    abstract val signed: String?

    abstract var issuer: String?
    abstract var subject: String?

    //fun sign()
    // also see `fun signAndDisclose` from `SelectivelyDisclosable.kt`

    abstract suspend fun verify(publicKey: Key): Result<JsonElement>
}
