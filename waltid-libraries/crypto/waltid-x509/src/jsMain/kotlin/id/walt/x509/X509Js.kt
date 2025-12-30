package id.walt.x509

actual fun validateCertificateChain(
    leaf: CertificateDer,
    chain: List<CertificateDer>,
    trustAnchors: List<CertificateDer>?,
    enableTrustedChainRoot: Boolean,
    enableSystemTrustAnchors: Boolean,
    enableRevocation: Boolean
) {
    // TODO: Implement with Web APIs or a JS PKI lib (no native PKIX path builder in WebCrypto).
    throw X509ValidationException("Not implemented on JS yet.")
}
