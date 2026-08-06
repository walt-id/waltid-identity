package id.walt.openid4vci.clientauth.attestation.verifier

import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.Key
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class Crypto2KeyBasedClientAttestationVerifier(
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
        val algorithm = runCatching {
            JwsAlgorithm.parse(requireNotNull(header["alg"]?.jsonPrimitive?.contentOrNull))
        }.getOrElse {
            return ClientAttestationVerificationResult.Rejected("Client attestation algorithm is invalid")
        }
        for (key in trustedKeys) {
            try {
                CompactJws.verify(jwt, key, algorithm)
                return ClientAttestationVerificationResult.Verified
            } catch (cause: CancellationException) {
                throw cause
            } catch (_: Throwable) {
                // Try the next explicitly trusted key.
            }
        }
        return ClientAttestationVerificationResult.Rejected("Client attestation signature is invalid")
    }
}
