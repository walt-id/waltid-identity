package id.walt.openid4vci.metadata.issuer

import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.CryptographicBindingMethod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Representation of a credential_configuration_supported entry (OpenID4VCI 1.0).
 */
@Serializable
data class CredentialConfiguration(
    val id: String,
    val format: CredentialFormat,
    @SerialName("scope")
    val scope: String? = null,
    @SerialName("credential_definition")
    val credentialDefinition: CredentialDefinition? = null,
    @SerialName("doctype")
    val doctype: String? = null,
    @SerialName("vct")
    val vct: String? = null,
    @SerialName("credential_signing_alg_values_supported")
    @Serializable(SigningAlgIdSetSerializer::class)
    val credentialSigningAlgValuesSupported: Set<SigningAlgId>? = null,
    @SerialName("cryptographic_binding_methods_supported")
    val cryptographicBindingMethodsSupported: Set<CryptographicBindingMethod>? = null,
    @SerialName("proof_types_supported")
    val proofTypesSupported: Map<String, ProofType>? = null,
    @SerialName("credential_metadata")
    val credentialMetadata: CredentialMetadata? = null,
    @SerialName("display")
    val display: List<CredentialDisplay>? = null,
) {
    init {
        require(id.isNotBlank()) { "credential configuration id must not be blank" }
        scope?.let { value ->
            require(value.isNotBlank()) { "scope must not be blank" }
        }
        display?.let { entries ->
            val locales = entries.mapNotNull { it.locale }
            require(locales.size == locales.distinct().size) {
                "display entries must not duplicate locales"
            }
        }
        cryptographicBindingMethodsSupported?.let { methods ->
            require(methods.isNotEmpty()) {
                "cryptographic_binding_methods_supported must not be empty"
            }
        }
        proofTypesSupported?.let { proofs ->
            require(proofs.isNotEmpty()) {
                "proof_types_supported must not be empty"
            }
            require(proofs.keys.all { it.isNotBlank() }) {
                "proof_types_supported keys must not be blank"
            }
        }
        if (cryptographicBindingMethodsSupported != null) {
            require(proofTypesSupported != null) {
                "proof_types_supported must be present when cryptographic_binding_methods_supported is set"
            }
        }
        if (proofTypesSupported != null) {
            require(cryptographicBindingMethodsSupported != null) {
                "cryptographic_binding_methods_supported must be present when proof_types_supported is set"
            }
        }
        credentialSigningAlgValuesSupported?.let { algorithms ->
            require(algorithms.isNotEmpty()) {
                "credential_signing_alg_values_supported must not be empty"
            }
            require(
                algorithms.none {
                    (it is SigningAlgId.Jose && it.value.isBlank()) ||
                        (it is SigningAlgId.LdSuite && it.value.isBlank()) ||
                        (it is SigningAlgId.CoseName && it.value.isBlank())
                }
            ) {
                "credential_signing_alg_values_supported must not contain blank entries"
            }
            when (format) {
                CredentialFormat.LDP_VC ->
                    require(algorithms.all { it is SigningAlgId.LdSuite }) {
                        "credential_signing_alg_values_supported must contain LD suite identifiers for ${format.value}"
                    }
                CredentialFormat.MSO_MDOC ->
                    require(algorithms.all { it is SigningAlgId.CoseValue || it is SigningAlgId.CoseName }) {
                        "credential_signing_alg_values_supported must contain COSE identifiers for ${format.value}"
                    }
                else ->
                    require(algorithms.all { it is SigningAlgId.Jose }) {
                        "credential_signing_alg_values_supported must contain JOSE identifiers for ${format.value}"
                    }
            }
        }
    }
}

/**
 * Format-specific credential definition metadata.
 */
@Serializable
data class CredentialDefinition(
    @SerialName("@context")
    val context: List<String>? = null,
    val type: List<String>? = null,
)

/**
 * Metadata about a supported proof type.
 */
@Serializable
data class ProofType(
    @SerialName("proof_signing_alg_values_supported")
    val proofSigningAlgValuesSupported: Set<String>,
    @SerialName("key_attestations_required")
    val keyAttestationsRequired: KeyAttestationsRequired? = null,
) {
    init {
        require(proofSigningAlgValuesSupported.isNotEmpty()) {
            "proof_signing_alg_values_supported must not be empty"
        }
        require(proofSigningAlgValuesSupported.none { it.isBlank() }) {
            "proof_signing_alg_values_supported must not contain blank entries"
        }
    }
}

@Serializable
data class KeyAttestationsRequired(
    @SerialName("key_storage")
    val keyStorage: Set<String>? = null,
    @SerialName("user_authentication")
    val userAuthentication: Set<String>? = null,
) {
    init {
        keyStorage?.let { values ->
            require(values.isNotEmpty()) {
                "key_attestations_required.key_storage must not be empty"
            }
            require(values.none { it.isBlank() }) {
                "key_attestations_required.key_storage must not contain blank entries"
            }
        }
        userAuthentication?.let { values ->
            require(values.isNotEmpty()) {
                "key_attestations_required.user_authentication must not be empty"
            }
            require(values.none { it.isBlank() }) {
                "key_attestations_required.user_authentication must not contain blank entries"
            }
        }
    }
}

@Serializable
data class CredentialMetadata(
    @SerialName("display")
    val display: List<CredentialDisplay>? = null,
    @SerialName("claims")
    val claims: List<ClaimDescription>? = null,
) {
    init {
        display?.let { entries ->
            require(entries.isNotEmpty()) {
                "credential_metadata.display must not be empty"
            }
            val locales = entries.mapNotNull { it.locale }
            require(locales.size == locales.distinct().size) {
                "credential_metadata.display entries must not duplicate locales"
            }
        }
        claims?.let { entries ->
            require(entries.isNotEmpty()) {
                "credential_metadata.claims must not be empty"
            }
        }
    }
}

/**
 * Claim description object (Appendix B.2).
 */
@Serializable
data class ClaimDescription(
    val path: List<String>,
    val mandatory: Boolean? = null,
    val display: List<ClaimDisplay>? = null,
) {
    init {
        require(path.isNotEmpty()) { "claim description path must not be empty" }
        require(path.none { it.isBlank() }) { "claim description path must not contain blank segments" }
        display?.let { entries ->
            require(entries.isNotEmpty()) { "claim description display must not be empty" }
            val locales = entries.mapNotNull { it.locale }
            require(locales.size == locales.distinct().size) {
                "claim description display entries must not duplicate locales"
            }
        }
    }
}

@Serializable
data class ClaimDisplay(
    val name: String? = null,
    val locale: String? = null,
) {
    init {
        name?.let { require(it.isNotBlank()) { "claim display name must not be blank" } }
        locale?.let { require(it.isNotBlank()) { "claim display locale must not be blank" } }
    }
}

/**
 * Display metadata for a supported credential.
 */
@Serializable
data class CredentialDisplay(
    val name: String,
    val locale: String? = null,
    val logo: CredentialDisplayLogo? = null,
    val description: String? = null,
    @SerialName("background_color")
    val backgroundColor: String? = null,
    @SerialName("background_image")
    val backgroundImage: CredentialDisplayBackgroundImage? = null,
    @SerialName("text_color")
    val textColor: String? = null,
) {
    init {
        require(name.isNotBlank()) { "display name must not be blank" }
        locale?.let { require(it.isNotBlank()) { "display locale must not be blank" } }
    }
}

@Serializable
data class CredentialDisplayLogo(
    val uri: String,
    @SerialName("alt_text")
    val altText: String? = null,
) {
    init {
        require(uri.isNotBlank()) { "logo uri must not be blank" }
        require(uri.contains(":")) { "logo uri must include a scheme" }
    }
}

@Serializable
data class CredentialDisplayBackgroundImage(
    val uri: String,
) {
    init {
        require(uri.isNotBlank()) { "background_image uri must not be blank" }
        require(uri.contains(":")) { "background_image uri must include a scheme" }
    }
}
