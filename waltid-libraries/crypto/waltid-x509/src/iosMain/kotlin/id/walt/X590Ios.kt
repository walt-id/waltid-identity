package id.walt.x509

actual fun parseX5cBase64(x5cBase64: List<String>): List<CertificateDer> =
    x5cBase64.map { CertificateDer(kotlin.io.encoding.Base64.Default.decode(it)) }

actual fun validateCertificateChain(
    leaf: CertificateDer,
    chain: List<CertificateDer>,
    trustAnchors: List<CertificateDer>?,
    enableRevocation: Boolean
) {
    // TODO: Implement with Web APIs or a JS PKI lib (no native PKIX path builder in WebCrypto).
    throw X509ValidationException("Not implemented on JS yet.")
}
