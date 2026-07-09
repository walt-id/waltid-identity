package id.walt.certificate.x509.node

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateParser
import kotlinx.io.bytestring.ByteString

class NodeX509CertificateParser : X509CertificateParser {

    override fun parseCertificatePem(pem: String): X509Certificate {
        TODO("Not yet implemented")
    }

    override fun parseCertificateDerEncoded(derEncoded: ByteString): X509Certificate {
        TODO("Not yet implemented")
    }
}