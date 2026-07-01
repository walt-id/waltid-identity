package id.walt.openid4vci.clientauth.attestation

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

class DefaultClientAttestationVerifier(
    private val trustResolver: ClientAttestationTrustResolver,
) : ClientAttestationVerifier {
    override suspend fun verifyAttestationJwt(
        jwt: String,
        header: JsonObject,
        payload: JsonObject,
    ): ClientAttestationVerificationResult {
        val trustedKeys = trustResolver.resolveTrustedAttesterKeys(header, payload)
        if (trustedKeys.isEmpty()) {
            return ClientAttestationVerificationResult.Rejected("Client attester is not trusted")
        }

        for (key in trustedKeys) {
            if (key.verifyJws(jwt).isSuccess) {
                return ClientAttestationVerificationResult.Verified
            }
        }

        return ClientAttestationVerificationResult.Rejected("Client attestation signature is invalid")
    }
}
