package id.walt.x509

@Throws(X509ValidationException::class)
actual fun validateCertificateChain(
    leaf: CertificateDer,
    chain: List<CertificateDer>,
    trustAnchors: List<CertificateDer>?,
    enableTrustedChainRoot: Boolean,
    enableSystemTrustAnchors: Boolean,
    enableRevocation: Boolean
) {
    // TODO(iOS): Implement certificate chain validation, then remove the guarded iOS X.509 tests.
    throw X509ValidationException("Certificate chain validation is not implemented on iOS yet.")
}
