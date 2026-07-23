package id.walt.certificate.x509

/**
 * Should be used for testing purposes only to verify signatures.
 *
 * Implementation should be platform-native.
 */
expect object SignatureValidationUtil {
    fun verifyPemChain(chainPem: String, selfSignedCaPem: String)

    suspend fun validateCertificateSignature(
        issuerPublicKey: X509Certificate.SubjectPublicKeyInfo,
        certificate: X509Certificate
    ): Boolean
}