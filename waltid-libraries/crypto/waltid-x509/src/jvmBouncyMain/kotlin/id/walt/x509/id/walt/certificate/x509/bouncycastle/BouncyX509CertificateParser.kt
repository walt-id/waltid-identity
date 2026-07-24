package id.walt.x509.id.walt.certificate.x509.bouncycastle

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateParser
import kotlinx.io.bytestring.ByteString
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.openssl.PEMParser
import java.io.StringReader


 class BouncyX509CertificateParser : X509CertificateParser {

    override fun parseCertificatePem(pem: String): X509Certificate {
        val result = runCatching {
            StringReader(pem).use { reader ->
                val parser = PEMParser(reader)
                parser.readObject()
            }
        }
        if (result.isFailure) {
            throw IllegalArgumentException("Failed to parse PEM: '${pem}'", result.exceptionOrNull())
        }
        val cert = result.getOrThrow()
        require(cert is X509CertificateHolder) { "Not a certificate in PEM" }
        return BouncyX509Certificate(cert)
    }

    override fun parseCertificateDerEncoded(derEncoded: ByteString): X509Certificate =
        BouncyX509Certificate(X509CertificateHolder(derEncoded.toByteArray()))
}