package id.walt.certificate.x509

import id.walt.certificate.x509.extension.SubjectKeyIdentifierExtension.Companion.extensionSubjectKeyIdentifier
import kotlinx.io.bytestring.ByteString

interface X509CertificateTrustStore {

    fun findCertificateBySubjectDn(
        subjectDn: String
    ): List<X509Certificate>

    fun findCertificate(subjectDn: String, subjectKeyId: ByteString? = null): List<X509Certificate> =
        findCertificateBySubjectDn(subjectDn).filter { x509Certificate: X509Certificate ->
            subjectKeyId?.let { ski ->
                if (x509Certificate.data.extensionSubjectKeyIdentifier?.keyIdentifier == ski) {
                    true
                } else {
                    false
                }
            } ?: true
        }
}