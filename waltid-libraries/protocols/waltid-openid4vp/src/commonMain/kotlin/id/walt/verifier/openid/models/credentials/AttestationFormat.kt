package id.walt.verifier.openid.models.credentials

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Formats for Verifier Attestations.
 * See Section 5.1 (verifier_attestations) of OpenID4VP spec.
 */
@Serializable
enum class AttestationFormat {
    @SerialName("jwt")
    JWT,
    // Other attestation formats can be added here
}
