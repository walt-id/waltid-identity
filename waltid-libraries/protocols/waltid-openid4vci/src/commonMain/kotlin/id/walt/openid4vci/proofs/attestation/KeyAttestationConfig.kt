package id.walt.openid4vci.proofs.attestation

import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.proofs.CredentialProofCapabilities
import id.walt.openid4vci.proofs.CredentialProofHandlers
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Deployment trust for credential keys, independently configured from OAuth client attestation. */
@Serializable
data class KeyAttestationConfig(
    val verificationMethod: KeyAttestationVerificationMethod,
    /** Maximum entries in each attested_keys array; defaults to 20. Explicit null disables the limit. */
    val maxAttestedKeys: Int? = 20,
) {
    init { require(maxAttestedKeys == null || maxAttestedKeys > 0) { "maxAttestedKeys must be positive" } }
}

@Serializable
sealed class KeyAttestationVerificationMethod {
    @Serializable
    @SerialName("static-jwk")
    data class StaticJwk(val jwk: JsonObject) : KeyAttestationVerificationMethod()

    @Serializable
    @SerialName("key-reference")
    data class KeyReference(val reference: String) : KeyAttestationVerificationMethod() {
        init { require(reference.isNotBlank()) { "Key attestation key reference must not be blank" } }
    }

    @Serializable
    @SerialName("x509-chain")
    data class X509Chain(
        val trustedRootCertificatesPem: List<String>,
        /**
         * Optional entity-type name. This library stores it and does not enforce it.
         * A deployment that wraps the verifier, such as Enterprise Issuer2, may require
         * a linked trust registry to trust the leaf and use this name as an extra filter.
         */
        val expectedEntityType: String? = null,
    ) : KeyAttestationVerificationMethod() {
        init {
            require(trustedRootCertificatesPem.isNotEmpty()) { "Key attestation trust roots must not be empty" }
            expectedEntityType?.let { require(it.isNotBlank()) { "expectedEntityType must not be blank" } }
        }
    }
}

fun validateKeyAttestationConfiguration(
    configurations: Iterable<CredentialConfiguration>,
    config: KeyAttestationConfig?,
    nonceValidationConfigured: Boolean = false,
    handlers: CredentialProofHandlers = CredentialProofHandlers.defaults(),
) {
    handlers.validateConfiguration(configurations, CredentialProofCapabilities(config != null, nonceValidationConfigured))
}

suspend fun KeyAttestationConfig.toVerificationOptions(
    keyReferenceResolver: KeyAttestationKeyReferenceResolver? = null,
): KeyAttestationVerificationOptions = KeyAttestationVerificationOptions(
    createKeyAttestationTrustResolver(verificationMethod, keyReferenceResolver),
    maxAttestedKeys = maxAttestedKeys,
)
