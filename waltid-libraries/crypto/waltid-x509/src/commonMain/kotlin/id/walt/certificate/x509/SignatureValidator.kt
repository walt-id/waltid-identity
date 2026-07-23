package id.walt.certificate.x509

interface SignatureValidator {

    val name: String

    suspend fun validateCertificateSignature(
        issuerPublicKey: X509Certificate.SubjectPublicKeyInfo,
        certificate: X509Certificate
    ): Boolean

    suspend fun validateCsrSignature(
        subjectPublicKey: PublicKeyInfo,
        certificate: X509Certificate
    ): Boolean
}