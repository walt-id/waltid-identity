package id.walt.openid4vci.dpop

/**
 * Inputs needed to verify one RFC 9449 DPoP proof. [targetUri] must come from
 * trusted server configuration, not from a client-controlled forwarding header.
 */
data class DPoPProofVerificationRequest(
    val proofJwt: String,
    val method: String,
    val targetUri: String,
    val accessToken: String? = null,
)

data class VerifiedDPoPProof(
    val jwkThumbprint: String,
)

/** Verifies possession of the DPoP key used to protect an OAuth request. */
fun interface DPoPProofVerifier {
    suspend fun verify(request: DPoPProofVerificationRequest): VerifiedDPoPProof
}
