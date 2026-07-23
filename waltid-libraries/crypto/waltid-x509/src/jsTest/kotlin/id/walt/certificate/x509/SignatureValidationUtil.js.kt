package id.walt.certificate.x509

actual object SignatureValidationUtil {
    actual fun verifyPemChain(chainPem: String, selfSignedCaPem: String) {
        //TODO
    }

    actual suspend fun validateCertificateSignature(
        issuerPublicKey: X509Certificate.SubjectPublicKeyInfo,
        certificate: X509Certificate
    ): Boolean {
        TODO("Not yet implemented")
    }

}