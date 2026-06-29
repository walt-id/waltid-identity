package id.walt.x509

import id.walt.crypto.keys.Key

actual suspend fun platformBuildCertificateSigningRequest(
    profileData: CertificateSigningRequestProfileData,
    signingKey: Key,
): CertificateSigningRequestBundle {
    // TODO(iOS): Implement CSR creation.
    TODO("CSR creation is not implemented for iOS")
}

actual suspend fun parseCertificateSigningRequest(
    csrDer: CertificateSigningRequestDer,
): DecodedCertificateSigningRequest {
    // TODO(iOS): Implement CSR parsing.
    TODO("CSR parsing is not implemented for iOS")
}
