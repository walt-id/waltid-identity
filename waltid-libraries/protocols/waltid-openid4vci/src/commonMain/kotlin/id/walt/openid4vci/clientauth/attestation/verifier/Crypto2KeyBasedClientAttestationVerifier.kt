package id.walt.openid4vci.clientauth.attestation.verifier

import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.Key
import io.github.oshai.kotlinlogging.KotlinLogging
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
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Throwable) {
                log.debug(cause) { "Client attestation signature did not verify with trusted key ${key.id.value}" }
                continue
            }
            // A profile permitting omission must not acquire a public-export requirement.
            if ("status" !in payload) return ClientAttestationVerificationResult.Verified
            return try {
                ClientAttestationVerificationResult.VerifiedWithSigner(key.exportPublicJwkObject())
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                log.debug(cause) { "Verified client attester's public status signer context is unavailable" }
                // Signature trust is established. Status validation fails closed as a technical
                // failure when a supplied reference has no trusted public signer context.
                ClientAttestationVerificationResult.Verified
            }
        }
        return ClientAttestationVerificationResult.Rejected("Client attestation signature is invalid")
    }

    companion object {
        private val log = KotlinLogging.logger {}
    }
}
