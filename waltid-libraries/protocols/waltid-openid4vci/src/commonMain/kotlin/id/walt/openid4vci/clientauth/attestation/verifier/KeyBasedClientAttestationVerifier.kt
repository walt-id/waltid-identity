package id.walt.openid4vci.clientauth.attestation.verifier

import id.walt.crypto.keys.Key
import kotlinx.serialization.json.JsonObject

class KeyBasedClientAttestationVerifier(
    private val trustedAttesterKeys: suspend (
        header: JsonObject,
        payload: JsonObject,
    ) -> List<Key>,
) : ClientAttestationVerifier {

    override suspend fun verifyAttestationJwt(
        jwt: String,
        header: JsonObject,
        payload: JsonObject,
    ): ClientAttestationVerificationResult {
        val trustedKeys = trustedAttesterKeys(header, payload)
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
