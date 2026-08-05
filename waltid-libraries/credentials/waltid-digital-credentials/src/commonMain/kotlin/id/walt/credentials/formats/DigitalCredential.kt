package id.walt.credentials.formats

import id.walt.credentials.signatures.CredentialSignature
import id.walt.crypto.keys.Key
import id.walt.crypto2.keys.Key as Crypto2Key
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Representation of a Digital Credential
 */
@Serializable
sealed class DigitalCredential : Crypto2DigitalCredential {
    abstract val format: String

    abstract val credentialData: JsonObject
    abstract val signature: CredentialSignature?
    abstract val signed: String?

    abstract var issuer: String?

    abstract var subject: String?

    /**
     * Get public key from signer (issuer) for signed credentials
     */
    @Deprecated("Use getSignerCrypto2Key()")
    abstract suspend fun getSignerKey(): Key?

    /**
     * Get holder's public key from the credential's `cnf` claim (or equivalent), if present.
     * Returns null when the credential has no cryptographic holder binding.
     * Overridden by credential types that support holder binding (SD-JWT VC, W3C+SD-JWT).
     */
    open suspend fun getHolderKey(): Key? = null

    abstract override suspend fun getHolderCrypto2Key(): Crypto2Key?

    // TODO: Signer key should globally move into signature, as such "open" key word to be removed in the future
    //open suspend fun getSignerKey(): Key? = signature?.signerKey

    @Deprecated("Use verifyCrypto2(publicKey, allowedAlgorithms)")
    abstract suspend fun verify(publicKey: Key): Result<JsonElement>

    //fun sign()
}
