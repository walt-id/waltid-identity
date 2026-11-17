package id.walt.openid4vci.proofs.attestation

import id.walt.openid4vci.attestation.AttestationStatusPolicy
import id.walt.openid4vci.attestation.AttestationStatusResolver

import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.proofs.CredentialProofCapabilities
import id.walt.openid4vci.proofs.CredentialProofHandlers
import id.walt.trust.decision.TrustDecisionEvaluator
import id.walt.trust.model.TrustedEntityType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Deployment trust for credential keys, independently configured from OAuth client attestation. */
@Serializable
data class KeyAttestationConfig(
    val verificationMethod: KeyAttestationVerificationMethod,
    /** Maximum entries in each attested_keys array; defaults to 20. Explicit null disables the limit. */
    val maxAttestedKeys: Int? = 20,
    val statusPolicy: AttestationStatusPolicy = AttestationStatusPolicy(),
) {
    init {
        require(maxAttestedKeys == null || maxAttestedKeys > 0) { "maxAttestedKeys must be positive" }
    }
}

@Serializable
sealed class KeyAttestationVerificationMethod {
    @Serializable
    @SerialName("static-jwk")
    data class StaticJwk(val jwk: JsonObject) : KeyAttestationVerificationMethod()

    @Serializable
    @SerialName("key-reference")
    data class KeyReference(val reference: String) : KeyAttestationVerificationMethod() {
        init {
            require(reference.isNotBlank()) { "Key attestation key reference must not be blank" }
        }
    }

    @Serializable
    @SerialName("x509-chain")
    data class X509Chain(
        val trustedRootCertificatesPem: List<String>,
    ) : KeyAttestationVerificationMethod() {
        init {
            require(trustedRootCertificatesPem.isNotEmpty()) { "Key attestation trust roots must not be empty" }
        }
    }

    /**
     * Trusts the attester when a trust registry lists its x5c chain, leaf first, for one of
     * [expectedEntityTypes], from an accepted source that is fresh. The deployment supplies the registry
     * as a `TrustDecisionEvaluator` to [toVerificationOptions].
     *
     * @property requireAuthenticatedSource also require the trust list's signature to be authenticated.
     */
    @Serializable
    @SerialName("x509-trust-registry")
    data class X509TrustRegistry(
        val expectedEntityTypes: Set<TrustedEntityType>,
        val requireAuthenticatedSource: Boolean = false,
    ) : KeyAttestationVerificationMethod() {
        init {
            require(expectedEntityTypes.isNotEmpty()) { "Key attestation expected entity types must not be empty" }
        }
    }
}

fun validateKeyAttestationConfiguration(
    configurations: Iterable<CredentialConfiguration>,
    config: KeyAttestationConfig?,
    nonceValidationConfigured: Boolean = false,
    handlers: CredentialProofHandlers = CredentialProofHandlers.defaults(),
) {
    handlers.validateConfiguration(
        configurations,
        CredentialProofCapabilities(config != null, nonceValidationConfigured)
    )
}

suspend fun KeyAttestationConfig.toVerificationOptions(
    keyReferenceResolver: KeyAttestationKeyReferenceResolver? = null,
    statusResolver: AttestationStatusResolver? = null,
    /** Required by [KeyAttestationVerificationMethod.X509TrustRegistry]; checked when an attestation is verified. */
    trustEvaluator: TrustDecisionEvaluator? = null,
): KeyAttestationVerificationOptions = KeyAttestationVerificationOptions(
    createKeyAttestationTrustResolver(verificationMethod, keyReferenceResolver, trustEvaluator),
    maxAttestedKeys = maxAttestedKeys,
    statusPolicy = statusPolicy,
    statusResolver = statusResolver,
)
