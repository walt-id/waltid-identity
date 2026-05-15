package id.walt.x509

internal actual suspend fun platformGenerateCertificateSigningRequest(
    spec: X509CertificateSigningRequestSpec,
): CertificateSigningRequestDer {
    throw NotImplementedError("CSR generation is not implemented on JS yet.")
}

internal actual suspend fun platformParseCertificateSigningRequest(
    csr: CertificateSigningRequestDer,
): X509DecodedCertificateSigningRequest {
    throw NotImplementedError("CSR parsing is not implemented on JS yet.")
}

internal actual suspend fun platformIsCertificateSigningRequestSignatureValid(
    csr: CertificateSigningRequestDer,
): Boolean {
    throw X509ValidationException("CSR signature validation is not implemented on JS yet.")
}
