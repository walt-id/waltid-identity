package id.walt.x509.id.walt.x509

import id.walt.x509.CertificateDer
import id.walt.x509.X509CertificateHandle
import java.security.cert.X509Certificate

data class JcaX509CertificateHandle(
    val certificate: X509Certificate,
) : X509CertificateHandle {

    override fun getCertificateDer() = CertificateDer(certificate.encoded)
}