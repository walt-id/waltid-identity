package id.walt.openid4vci.deferred

import id.walt.openid4vci.attestation.VerifiedAttestationStatus

import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.exportPublicJwk
import id.walt.openid4vci.attestation.VerifiedAttestationLifetime
import id.walt.openid4vci.proofs.ProofType
import id.walt.openid4vci.proofs.attestation.restoreAttestedKey
import id.walt.openid4vci.requests.credential.AcceptedCredentialRequest
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class AcceptedKeyAttestation(
    val lifetime: VerifiedAttestationLifetime,
    val attestedKeyThumbprints: Set<String>,
    val status: VerifiedAttestationStatus? = null,
)

@Serializable
data class AcceptedCredentialProof(
    val proofType: String,
    val keyAttestation: AcceptedKeyAttestation? = null,
)

/** Validates accepted evidence only; never replays authentication, proofs or nonces. */
class DeferredAttestationLifetimeValidator {
    suspend fun isValid(
        accepted: AcceptedCredentialRequest,
        now: Instant,
        clientAttestationRequired: Boolean = false,
    ): Boolean {
        val clientAttestation = accepted.clientAttestation
        val wiaRequired = accepted.clientAttestationRequired ?: (clientAttestationRequired && !accepted.request.anonymousPreAuthorizedAccess)
        if (wiaRequired && clientAttestation == null) return false
        if (clientAttestation != null && (clientAttestation.subject != accepted.request.accessTokenClientId ||
                !clientAttestation.lifetime.clientAttestationValidAt(now))) return false

        val proofs = accepted.proofs
        if (proofs == null) return accepted.configuration.proofTypesSupported.orEmpty().none { (type, metadata) ->
            type == ProofType.ATTESTATION.value || metadata.keyAttestationsRequired != null
        }
        val requiresProof = accepted.configuration.proofTypesSupported.orEmpty().isNotEmpty()
        if (requiresProof && (proofs.isEmpty() || accepted.bindings.isEmpty())) return false
        for (proof in proofs) {
            val attestation = proof.keyAttestation
            val required = proof.proofType == ProofType.ATTESTATION.value ||
                accepted.configuration.proofTypesSupported?.get(proof.proofType)?.keyAttestationsRequired != null
            if (required && attestation == null) return false
            if (attestation != null && (!attestation.lifetime.keyAttestationValidAt(now) ||
                    proof.proofType == ProofType.JWT.value && attestation.lifetime.expiresAt == null)) return false
        }
        for (binding in accepted.bindings) {
            if (requiresProof && binding.proofIndexes.isEmpty()) return false
            val thumbprint = Jwk.sha256Thumbprint(restoreAttestedKey(binding.publicJwk).exportPublicJwk())
            for (index in binding.proofIndexes) {
                val proof = proofs.getOrNull(index) ?: return false
                if (proof.keyAttestation?.attestedKeyThumbprints?.contains(thumbprint) == false) return false
            }
        }
        return true
    }
}
