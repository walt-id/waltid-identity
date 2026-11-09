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
    /** Signature trust is established; no public signer context was captured for status checks. */
    data object Verified : ClientAttestationVerificationResult
    /** Public key which actually verified the signature, supplied by the trusted verifier. */
    data class VerifiedWithSigner(val publicJwk: JsonObject) : ClientAttestationVerificationResult
    data class Rejected(val reason: String? = null) : ClientAttestationVerificationResult
}
