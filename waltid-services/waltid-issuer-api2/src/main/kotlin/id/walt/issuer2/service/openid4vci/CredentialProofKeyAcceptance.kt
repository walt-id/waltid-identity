package id.walt.issuer2.service.openid4vci

import id.walt.issuer2.domain.IssuanceSession
import kotlinx.serialization.json.JsonObject

/** Accepts the ordered public keys from all validated credential proofs before credentials are constructed. */
fun interface CredentialProofKeyAcceptance {
    suspend fun accept(session: IssuanceSession, proofPublicKeyJwks: List<JsonObject>): Boolean
}

/** Atomically commits proof-key side effects only after the complete credential batch was constructed. */
fun interface CredentialProofKeyCommitment {
    suspend fun commit(session: IssuanceSession, proofPublicKeyJwks: List<JsonObject>): Boolean
}

class CredentialProofKeyAcceptanceException(
    message: String,
    val retryable: Boolean,
) : IllegalArgumentException(message)
