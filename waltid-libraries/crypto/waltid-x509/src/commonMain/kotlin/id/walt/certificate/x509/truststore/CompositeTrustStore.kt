package id.walt.certificate.x509.truststore

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateParser
import id.walt.certificate.x509.X509CertificateTrustStore

class CompositeTrustStore(initialTrustStores: List<X509CertificateTrustStore>) : X509CertificateTrustStore {

    val trustStores = mutableListOf<X509CertificateTrustStore>()

    init {
        trustStores.addAll(initialTrustStores)
    }

    override suspend fun findCertificateBySubjectDn(
        subjectDn: String
    ): List<X509Certificate> =
        trustStores.flatMap {
            it.findCertificateBySubjectDn(
                subjectDn
            )
        }
}