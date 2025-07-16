package id.walt.verifier.openid.models.credentials

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Formats for Verifier Attestations.
 * See verifier_info of OpenID4VP spec.
 *
 * -> now "verifier_info" since draft 29
 */
@Serializable
enum class AttestationFormat {
    @SerialName("jwt")
    JWT,
    // Other attestation formats can be added here
}
