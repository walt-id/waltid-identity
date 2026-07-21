package id.walt.certificate.x509.signum

import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateParser
import kotlinx.io.bytestring.ByteString
import at.asitplus.signum.indispensable.pki.X509Certificate as SignumCertificate

class SignumCertificateParser : X509CertificateParser {

    override fun parseCertificatePem(pem: String): X509Certificate {
        val result = SignumCertificate.decodeFromPem(pem)
        if (result.isFailure) {
            throw IllegalArgumentException("Failed to parse certificate pem '${pem}'", result.exceptionOrNull())
        }
        return SignumX509Certificate(result.getOrThrow())
    }

    override fun parseCertificateDerEncoded(derEncoded: ByteString): X509Certificate {
        TODO("Not yet implemented")
    }
}