package id.walt.issuer2.service.openid4vci

import id.walt.issuer2.domain.IssuanceSession
import kotlinx.serialization.json.JsonObject

/** Accepts a validated credential proof's public key before the credential response is emitted. */
fun interface CredentialProofKeyAcceptance {
    suspend fun accept(session: IssuanceSession, proofPublicKeyJwk: JsonObject): Boolean
}

/** Commits proof-key side effects only after credential construction succeeds. */
fun interface CredentialProofKeyCommitment {
    suspend fun commit(session: IssuanceSession, proofPublicKeyJwk: JsonObject): Boolean
}

class CredentialProofKeyAcceptanceException(
    message: String,
    val retryable: Boolean,
) : IllegalArgumentException(message)
