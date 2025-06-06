package id.walt.credentials.signatures

import kotlinx.serialization.Serializable

@Serializable
sealed class CredentialSignature {

    /** verify signature for a signed credential string */
    //abstract suspend fun verifySignature(plaintext: ByteArray, publicKey: Key): Boolean

}
