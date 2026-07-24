package id.walt.x509.id.walt.certificate.x509.javasec

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateParser
import id.walt.certificate.x509.X509CertificateTrustStore
import kotlinx.io.bytestring.ByteString
import java.security.KeyStore
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class JavaDefaultTrustStore(private val parser: X509CertificateParser) : X509CertificateTrustStore {

    private val trustManagers: Collection<X509TrustManager> = loadTrustManagers()

    override fun findCertificateBySubjectDn(
        subjectDn: String
    ): List<X509Certificate> {
        val trusted = trustManagers.flatMap { trustManager ->
            val trustedIssuers = trustManager.acceptedIssuers.toList()
            trustedIssuers
                .map { parser.parseCertificateDerEncoded(ByteString(it.encoded)) }
                .filter { certificate ->
                    certificate.data.subjectDn == subjectDn
                }
        }
        return trusted
    }


    companion object {
        private fun loadTrustManagers(): Collection<X509TrustManager> {
            val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagerFactory.init(null as KeyStore?)
            return trustManagerFactory.trustManagers.map { it as? X509TrustManager }
                .filterNotNull()
        }
    }
}