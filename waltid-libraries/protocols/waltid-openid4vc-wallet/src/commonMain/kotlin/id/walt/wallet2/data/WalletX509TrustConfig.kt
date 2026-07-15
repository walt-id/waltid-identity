package id.walt.wallet2.data

import id.walt.openid4vp.clientidprefix.X509TrustPolicy
import id.walt.x509.CertificateDer
import kotlinx.serialization.Serializable

/**
 * Serializable, deployment-facing X.509 trust configuration for OID4VP Request Objects.
 * Trust anchors are PEM-encoded certificates controlled by the wallet deployment; certificates
 * supplied by a verifier in `x5c` are never promoted to trust anchors.
 */
@Serializable
data class WalletX509TrustConfig(
    val trustAnchorPemCertificates: List<String> = emptyList(),
    val enableSystemTrustAnchors: Boolean = false,
    val enableRevocation: Boolean = false,
    val allowedRequestObjectAlgorithms: Set<String> = emptySet(),
    val requireTrustAnchorOmittedFromX5c: Boolean = false,
    val rejectLeafTrustAnchor: Boolean = false,
) {
    init {
        require(trustAnchorPemCertificates.isNotEmpty() || enableSystemTrustAnchors) {
            "At least one PEM trust anchor or system trust anchors must be configured"
        }
    }

    fun toTrustPolicy(): X509TrustPolicy = X509TrustPolicy(
        trustAnchors = trustAnchorPemCertificates.map(CertificateDer::fromPEMEncodedString),
        enableSystemTrustAnchors = enableSystemTrustAnchors,
        enableRevocation = enableRevocation,
        allowedRequestObjectAlgorithms = allowedRequestObjectAlgorithms,
        requireTrustAnchorOmittedFromX5c = requireTrustAnchorOmittedFromX5c,
        rejectLeafTrustAnchor = rejectLeafTrustAnchor,
    )

    companion object {
        fun fromPolicy(policy: X509TrustPolicy): WalletX509TrustConfig = WalletX509TrustConfig(
            trustAnchorPemCertificates = policy.trustAnchors.map(CertificateDer::toPEMEncodedString),
            enableSystemTrustAnchors = policy.enableSystemTrustAnchors,
            enableRevocation = policy.enableRevocation,
            allowedRequestObjectAlgorithms = policy.allowedRequestObjectAlgorithms,
            requireTrustAnchorOmittedFromX5c = policy.requireTrustAnchorOmittedFromX5c,
            rejectLeafTrustAnchor = policy.rejectLeafTrustAnchor,
        )
    }
}
