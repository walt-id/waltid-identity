package id.walt.openid4vci.proofs

import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.ProofTypeMetadata
import id.walt.openid4vci.proofs.attestation.KeyAttestationUsage
import id.walt.openid4vci.proofs.attestation.KeyAttestationVerifier
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock
import kotlin.time.Instant

class AttestationCredentialProofHandler(
    clockSkewSeconds: Long = 60,
    now: () -> Instant = { Clock.System.now() },
) : CredentialProofHandler {
    override val proofType: ProofType = ProofType.ATTESTATION
    private val verifier = KeyAttestationVerifier(clockSkewSeconds, now)

    override fun validateConfiguration(
        proofMetadata: ProofTypeMetadata,
        configuration: CredentialConfiguration,
        capabilities: CredentialProofCapabilities,
    ) {
        require(capabilities.keyAttestation) { "Standalone attestation requires keyAttestationConfig trust material" }
        require(capabilities.issuerBoundNonce) { "Standalone attestation requires issuer-bound nonce validation" }
        require(configuration.format in setOf(CredentialFormat.SD_JWT_VC, CredentialFormat.MSO_MDOC)) {
            "Standalone attestation requires a credential handler supporting attested key bindings"
        }
        require(configuration.cryptographicBindingMethodsSupported?.any {
            it == CryptographicBindingMethod.Jwk || it == CryptographicBindingMethod.CoseKey
        } != false) { "Standalone attestation has no compatible binding method" }
    }

    override suspend fun verify(
        proof: JsonElement,
        proofMetadata: ProofTypeMetadata?,
        configuration: CredentialConfiguration,
        context: CredentialProofValidationContext,
    ): CredentialProofHandlerResult {
        val jwt = (proof as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: throw invalidCredentialProof("Attestation proof must be a string")
        val options = context.keyAttestation
            ?: throw CredentialProofServiceException("Standalone attestation trust is not configured")
        val evidence = verifier.verify(jwt, proofMetadata, context, configuration, options, KeyAttestationUsage.STANDALONE_PROOF)
        return CredentialProofHandlerResult(
            VerifiedAttestationProof(evidence),
            evidence.attestedKeys.map { VerifiedCredentialBindingCandidate(it) },
            CredentialBindingMultiplicity.DISTINCT_KEYS,
        )
    }
}
