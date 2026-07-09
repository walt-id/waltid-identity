package id.walt.certificate.x509

interface X509CertificateTrustStore {

    suspend fun findCertificateBySubjectDn(
        subjectDn: String
    ): List<X509Certificate>

}