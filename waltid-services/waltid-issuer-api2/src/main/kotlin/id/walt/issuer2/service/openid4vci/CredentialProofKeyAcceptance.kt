package id.walt.issuer2.service.openid4vci

import id.walt.issuer2.domain.IssuanceSession
import kotlinx.serialization.json.JsonObject

/** Accepts a validated credential proof's public key before the credential response is emitted. */
fun interface CredentialProofKeyAcceptance {
    suspend fun accept(session: IssuanceSession, proofPublicKeyJwk: JsonObject): Boolean
}
