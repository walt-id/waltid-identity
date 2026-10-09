package id.walt.certificate.x509.truststore

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateTrustStore
import kotlinx.io.bytestring.ByteString

/**
 * A trust store held in memory.
 *
 * Several certificates may share one subject DN (e.g. a CA key rollover); all of them are kept and
 * returned by [findCertificateBySubjectDn]. Certificates are identified by their SHA-256 fingerprint,
 * not their serial number: serial numbers are only unique per issuer, so two distinct CAs that happen
 * to use the same serial must not overwrite each other. Adding the same certificate twice is a no-op.
 */
class InMemoryTrustStore(trustedCertificates: List<X509Certificate> = emptyList()) : X509CertificateTrustStore {

    private val internalSubjectDnMap: MutableMap<String, MutableMap<ByteString, X509Certificate>> = mutableMapOf()

    init {
        trustedCertificates.forEach {
            addCertificate(it)
        }
    }

    fun isEmpty(): Boolean = internalSubjectDnMap.isEmpty()

    override fun findCertificateBySubjectDn(subjectDn: String): List<X509Certificate> =
        internalSubjectDnMap[subjectDn]?.values?.toList()
            ?: emptyList()

    fun addCertificate(cert: X509Certificate) {
        val certificatesForDn = internalSubjectDnMap.getOrPut(cert.data.subjectDn) { mutableMapOf() }
        certificatesForDn[cert.fingerprintSha256] = cert
    }
}
