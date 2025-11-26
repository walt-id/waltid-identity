package id.walt.credentials.formats

import id.walt.credentials.signatures.CredentialSignature
import id.walt.crypto.keys.Key
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Representation of a Digital Credential
 */
@Serializable
sealed class DigitalCredential {
    abstract val format: String

    abstract val credentialData: JsonObject
    abstract val signature: CredentialSignature?
    abstract val signed: String?

    abstract var issuer: String?

    abstract var subject: String?

    /**
     * Get public key from signer (issuer) for signed credentials
     */
    abstract suspend fun getSignerKey(): Key?

    // TODO: Signer key should globally move into signature, as such "open" key word to be removed in the future
    //open suspend fun getSignerKey(): Key? = signature?.signerKey

    abstract suspend fun verify(publicKey: Key): Result<JsonElement>

    //fun sign()
}
