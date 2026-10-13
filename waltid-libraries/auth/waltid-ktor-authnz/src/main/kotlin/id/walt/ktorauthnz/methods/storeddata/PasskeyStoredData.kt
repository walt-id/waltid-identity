package id.walt.ktorauthnz.methods.storeddata

import id.walt.ktorauthnz.methods.Passkey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A registered passkey: its public key and authenticator data, to verify its assertions. */
@Serializable
@SerialName("passkey")
data class PasskeyStoredData(
    /** WebAuthn attested credential data (credential id and public key), base64url. */
    val attestedCredentialData: String,
    /** Signature counter of the last assertion; a counter going back signals a cloned authenticator. */
    val signCount: Long,
    val userVerified: Boolean,
    val backupEligible: Boolean,
    val backedUp: Boolean,
    /** A name the user gave it, e.g. "work laptop". */
    val name: String? = null,
) : AuthMethodStoredData {
    override fun authMethod() = Passkey
}
