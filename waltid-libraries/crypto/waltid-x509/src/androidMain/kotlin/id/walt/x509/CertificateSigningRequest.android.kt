package id.walt.x509

actual suspend fun parseCertificateSigningRequest(csrDer: CertificateSigningRequestDer): DecodedCertificateSigningRequest {
    TODO("Not yet implemented")
}

@Deprecated(message = "Use CertificateSigningRequestBuilder.buildDer with a crypto2 key and an explicit SignatureAlgorithm.")
actual suspend fun platformBuildCertificateSigningRequest(
    profileData: CertificateSigningRequestProfileData,
    signingKey: id.walt.crypto.keys.Key
): CertificateSigningRequestBundle {
    TODO("Not yet implemented")
}