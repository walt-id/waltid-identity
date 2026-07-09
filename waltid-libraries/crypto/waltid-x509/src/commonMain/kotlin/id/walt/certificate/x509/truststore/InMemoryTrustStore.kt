package id.walt.certificate.x509.truststore

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateTrustStore
import kotlinx.io.bytestring.ByteString

class InMemoryTrustStore(trustedCertificates: List<X509Certificate> = emptyList()) : X509CertificateTrustStore {

    init {
        trustedCertificates.forEach {
            addCertificate(it)
        }
    }

    val internalSubjectDnMap: MutableMap<String, MutableMap<ByteString, X509Certificate>> = mutableMapOf()

    override suspend fun findCertificateBySubjectDn(subjectDn: String): List<X509Certificate> =
        internalSubjectDnMap[subjectDn]?.values?.toList()
            ?: emptyList()

    fun addCertificate(cert: X509Certificate) {
        val certificatesForDn = internalSubjectDnMap.getOrPut(cert.data.subjectDn) { mutableMapOf() }
        certificatesForDn[cert.data.serialNumberRaw] = cert
    }
}