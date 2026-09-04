package id.walt.x509

internal actual fun validatePlatformClientAuthenticationCertificateChain(
    leaf: CertificateDer,
    chain: List<CertificateDer>,
    trustAnchors: List<CertificateDer>,
) {
    validateCertificateChain(
        leaf = leaf,
        chain = chain,
        trustAnchors = trustAnchors,
        enableTrustedChainRoot = false,
        enableSystemTrustAnchors = false,
        enableRevocation = false,
    )
}
