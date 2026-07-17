package id.walt.x509

actual fun validateCertificateChain(
    leaf: CertificateDer,
    chain: List<CertificateDer>,
    trustAnchors: List<CertificateDer>?,
    enableTrustedChainRoot: Boolean,
    enableSystemTrustAnchors: Boolean,
    enableRevocation: Boolean
) {
    if (enableSystemTrustAnchors) {
        throw X509ValidationException("System trust anchors are not supported for JS certificate validation.")
    }
    if (enableRevocation) {
        throw X509ValidationException("Revocation checking is not supported for JS certificate validation.")
    }

    validateCertificateChainWithExplicitTrust(
        leaf = leaf,
        chain = chain,
        trustAnchors = trustAnchors,
        enableTrustedChainRoot = enableTrustedChainRoot,
    )
}
