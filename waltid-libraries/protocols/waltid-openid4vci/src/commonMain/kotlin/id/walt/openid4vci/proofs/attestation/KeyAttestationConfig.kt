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
    val limits: KeyAttestationLimits = KeyAttestationLimits(),
)

@Serializable
data class KeyAttestationLimits(
    val maxAttestedKeys: Int = 32,
    val maxCredentials: Int = 32,
    val maxJwtLength: Int = 65_536,
) {
    init {
        require(maxAttestedKeys > 0 && maxCredentials > 0 && maxJwtLength > 0) {
            "Key attestation limits must be positive"
        }
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
        init { require(reference.isNotBlank()) { "Key attestation key reference must not be blank" } }
    }

    @Serializable
    @SerialName("x509-chain")
    data class X509Chain(val trustedRootCertificatesPem: List<String>) : KeyAttestationVerificationMethod() {
        init { require(trustedRootCertificatesPem.isNotEmpty()) { "Key attestation trust roots must not be empty" } }
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
    limits,
)
