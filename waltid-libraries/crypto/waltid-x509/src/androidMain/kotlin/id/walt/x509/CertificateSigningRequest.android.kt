package id.walt.x509

actual suspend fun platformBuildCertificateSigningRequest(
    profileData: CertificateSigningRequestProfileData,
    signingKey: id.walt.crypto.keys.Key
): CertificateSigningRequestBundle {
    TODO("Not yet implemented")
}

actual suspend fun parseCertificateSigningRequest(csrDer: CertificateSigningRequestDer): DecodedCertificateSigningRequest {
    TODO("Not yet implemented")
}