package id.walt.openid4vci.proofs

import id.walt.crypto2.keys.Key
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.ProofTypeMetadata
import kotlinx.serialization.json.JsonElement

interface CredentialProofHandler {
    val proofType: ProofType

    suspend fun verify(
        proof: JsonElement,
        proofMetadata: ProofTypeMetadata?,
        configuration: CredentialConfiguration,
        context: CredentialProofValidationContext,
    ): CredentialProofHandlerResult

    /** Deployment requirements, checked by the registry's configuration validation. */
    fun validateConfiguration(
        proofMetadata: ProofTypeMetadata,
        configuration: CredentialConfiguration,
        capabilities: CredentialProofCapabilities,
    ) {}
}

data class CredentialProofCapabilities(
    val keyAttestation: Boolean,
    val issuerBoundNonce: Boolean,
)

data class VerifiedCredentialBindingCandidate(
    val holderKey: Key,
    val holderKid: String? = null,
    val holderDid: String? = null,
)

enum class CredentialBindingMultiplicity { PRESERVE_OCCURRENCES, DISTINCT_KEYS }

data class CredentialProofHandlerResult(
    val evidence: VerifiedCredentialProof,
    val candidates: List<VerifiedCredentialBindingCandidate>,
    val multiplicity: CredentialBindingMultiplicity,
)
