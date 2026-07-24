package id.walt.certificate.x509

import kotlinx.io.bytestring.ByteString

interface X509CertificateParser {

    fun parseCertificatePem(pem: String): X509Certificate

    fun parseCertificateDerEncoded(derEncoded: ByteString): X509Certificate
}