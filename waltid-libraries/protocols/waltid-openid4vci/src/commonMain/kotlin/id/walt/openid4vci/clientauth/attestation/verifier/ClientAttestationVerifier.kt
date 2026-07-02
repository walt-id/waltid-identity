package id.walt.openid4vci.clientauth.attestation.verifier

import kotlinx.serialization.json.JsonObject

interface ClientAttestationVerifier {
    suspend fun verifyAttestationJwt(
        jwt: String,
        header: JsonObject,
        payload: JsonObject,
    ): ClientAttestationVerificationResult
}

sealed interface ClientAttestationVerificationResult {
    data object Verified : ClientAttestationVerificationResult
    data class Rejected(val reason: String? = null) : ClientAttestationVerificationResult
}
