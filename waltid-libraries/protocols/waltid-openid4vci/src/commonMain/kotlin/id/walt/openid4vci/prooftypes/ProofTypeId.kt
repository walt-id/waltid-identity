package id.walt.openid4vci.prooftypes

/**
 * Proof type identifiers (OpenID4VCI 1.0).
 */
enum class ProofTypeId(val value: String) {
    JWT("jwt"),
    DI_VP("di_vp"),
    ATTESTATION("attestation");

    override fun toString(): String = value
}
