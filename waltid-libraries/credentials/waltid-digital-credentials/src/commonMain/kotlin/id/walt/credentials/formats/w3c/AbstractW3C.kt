package id.walt.credentials.formats

import id.walt.credentials.signatures.*
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.crypto.keys.Key
import kotlinx.serialization.Serializable

@Serializable
sealed class AbstractW3C(
) : DigitalCredential(), SelectivelyDisclosableVerifiableCredential {

    // TODO: issuer should move into signature, just use credential.signerKey from superclass
    override suspend fun getSignerKey(): Key? =
        when (signature) {
            null -> null
            is JwtBasedSignature -> (signature as JwtBasedSignature).getJwtBasedIssuer(credentialData)
            else -> throw NotImplementedError("Not yet implemented: Retrieve issuer key from SdJwtCredential with ${signature!!::class.simpleName} signature")
        }

    init {
        selfCheck()
    }

    override suspend fun verify(publicKey: Key) =
        when (signature) {
            is JwtCredentialSignature, is SdJwtCredentialSignature -> {
                require(signed != null) { "Cannot verify unsigned credential" }
                publicKey.verifyJws(signed!!)
            }

            is CoseCredentialSignature -> throw UnsupportedOperationException("COSE signature verification for W3C credentials is not yet implemented")
            is DataIntegrityProofCredentialSignature -> throw UnsupportedOperationException("Data Integrity Proof (LDP) signature verification for W3C credentials is not yet implemented")
            null -> throw IllegalArgumentException("Credential contains no signature, cannot verify")
        }
}

