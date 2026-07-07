package id.walt.x509.id.walt.certificate.x509.bouncycastle

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateParser
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.openssl.PEMParser
import java.io.StringReader


internal class BouncyX509CertificateParser : X509CertificateParser {

    override fun parseCertificatePem(pem: String): X509Certificate {
        val cert = StringReader(pem).use { reader ->
            val parser = PEMParser(reader)
            parser.readObject()
        }
        require(cert is X509CertificateHolder) { "Not a certificate in PEM" }
        return BouncyX509Certificate(cert as X509CertificateHolder)
    }
}