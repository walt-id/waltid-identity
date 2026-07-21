package id.walt.x509.id.walt.certificate.x509.bouncycastle

import id.walt.certificate.x509.Pkcs10CertificateSigningRequest
import id.walt.certificate.x509.Pkcs10CertificateSigningRequestParser
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.StringReader

class BouncyPkcs10CertificateSigningRequestParser : Pkcs10CertificateSigningRequestParser {
    override fun parseCertificateSigningRequestPem(pem: String): Pkcs10CertificateSigningRequest {
        val cert = StringReader(pem).use { reader ->
            val parser = PEMParser(reader)
            parser.readObject()
        }
        require(cert is PKCS10CertificationRequest) { "Not a certificate signing request in PEM" }
        return BouncyPkcs10CertificateSigningRequest(cert)
    }
}