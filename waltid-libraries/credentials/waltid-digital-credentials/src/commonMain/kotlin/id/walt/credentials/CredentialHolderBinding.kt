package id.walt.credentials

import id.walt.credentials.formats.AbstractW3C
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.resolveHolderCrypto2Key
import id.walt.credentials.signatures.JwtBasedSignature
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyEncodingFormat

/** Checks issuer-provided holder information against an authenticated presenter. */
object CredentialHolderBinding {
    suspend fun requireBoundToPresenter(
        credential: DigitalCredential,
        presenterDid: String?,
        presenterKeyThumbprint: String,
    ) {
        // Wrapped JWT credentials retain outer confirmation claims in the signed payload rather
        // than in credentialData. Check every explicit confirmation; none may fall back to a DID.
        val signedPayload = if (credential.signature is JwtBasedSignature) {
            requireNotNull(credential.signed).substringBefore('~').decodeJws().payload
        } else null
        val confirmations = listOfNotNull(credential.credentialData, signedPayload)
            .filter { "cnf" in it }.distinct()
        val holderKeys = confirmations.map { requireNotNull(it.resolveHolderCrypto2Key()) }
            .ifEmpty { listOfNotNull(credential.getHolderCrypto2Key()) }
        val bound = if (holderKeys.isNotEmpty()) {
            holderKeys.all { holderKey ->
                val publicKey = requireNotNull(holderKey.capabilities.publicKeyExporter)
                    .exportPublicKey(KeyEncodingFormat.JWK) as EncodedKey.Jwk
                Jwk.sha256Thumbprint(publicKey) == presenterKeyThumbprint
            }
        } else {
            val subjects = listOfNotNull(credential.subject,
                CredentialParser.getCredentialDataSubject(credential.credentialData),
                signedPayload?.let(CredentialParser::getJwtHeaderOrDataSubject)).distinct()
            credential is AbstractW3C && presenterDid?.startsWith("did:") == true && subjects == listOf(presenterDid)
        }
        require(bound) { "Credential is not cryptographically or semantically bound to presenter $presenterDid" }
    }
}
