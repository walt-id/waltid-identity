package id.walt.openid4vp.clientidprefix.prefixes

import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientValidationResult
import id.walt.openid4vp.clientidprefix.RequestContext
import kotlinx.serialization.Serializable

/**
 * Handles `verifier_attestation` prefix per OID4VP 1.0, Section 5.9.3 and §12.
 *
 * This prefix is deliberately rejected until the wallet can be configured with
 * trusted attestation issuers. Resolving a key from attacker-controlled JWT data
 * is not sufficient to authenticate a Verifier Attestation.
 */
@Serializable
data class VerifierAttestation(val sub: String, override val rawValue: String) : ClientId {

    suspend fun authenticateVerifierAttestation(
        clientId: VerifierAttestation,
        context: RequestContext,
    ): ClientValidationResult {
        return ClientValidationResult.Failure(
            ClientIdError.AttestationError(
                "No wallet-controlled trusted Verifier Attestation issuer policy is configured"
            )
        )
    }
}
