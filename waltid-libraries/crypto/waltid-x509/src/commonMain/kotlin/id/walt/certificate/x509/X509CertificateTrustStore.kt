package id.walt.certificate.x509

interface X509CertificateTrustStore {

    fun findCertificateBySubjectDn(
        subjectDn: String
    ): List<X509Certificate>

}