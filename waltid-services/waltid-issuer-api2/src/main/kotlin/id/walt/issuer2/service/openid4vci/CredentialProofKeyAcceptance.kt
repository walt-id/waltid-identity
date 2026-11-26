package id.walt.issuer2.service.openid4vci

import id.walt.issuer2.domain.IssuanceSession
import id.walt.openid4vci.responses.credential.IssuedCredential
import kotlinx.serialization.json.JsonObject

/** Accepts the ordered public keys from all validated credential proofs before credentials are constructed. */
fun interface CredentialProofKeyAcceptance {
    suspend fun accept(session: IssuanceSession, proofPublicKeyJwks: List<JsonObject>): Boolean
}

/** Atomically commits proof-key side effects only after the complete credential batch was constructed. */
fun interface CredentialProofKeyCommitment {
    suspend fun commit(session: IssuanceSession, proofPublicKeyJwks: List<JsonObject>): Boolean
}

/** A commitment that needs the issuer's completed credential batch, in proof-key order. */
fun interface IssuedCredentialCommitment : CredentialProofKeyCommitment {
    suspend fun commit(
        session: IssuanceSession,
        proofPublicKeyJwks: List<JsonObject>,
        issuedCredentials: List<IssuedCredential>,
    ): Boolean

    /** A caller that cannot supply the issued credentials cannot commit their side effects. */
    override suspend fun commit(session: IssuanceSession, proofPublicKeyJwks: List<JsonObject>): Boolean = false
}

class CredentialProofKeyAcceptanceException(
    message: String,
    val retryable: Boolean,
) : IllegalArgumentException(message)
