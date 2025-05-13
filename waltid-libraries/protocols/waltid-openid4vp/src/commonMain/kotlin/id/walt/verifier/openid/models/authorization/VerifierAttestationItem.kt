package id.walt.verifier.openid.models.authorization

import id.walt.verifier.openid.models.credentials.AttestationFormat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a Verifier Attestation object.
 * See: Section 5.1 (verifier_attestations parameter)
 */
@Serializable
data class VerifierAttestationItem(
    val format: AttestationFormat,
    val data: String, // Could be a JWT string or other format-specific data
    @SerialName("credential_ids")
    val credentialIds: List<String>? = null,
)
