package id.walt.certificate.x509.signum

import at.asitplus.signum.indispensable.asn1.Asn1Exception
import id.walt.certificate.x509.X509Certificate
import id.walt.certificate.x509.X509CertificateParser
import kotlinx.io.bytestring.ByteString
import at.asitplus.signum.indispensable.pki.X509Certificate as SignumCertificate

class SignumCertificateParser : X509CertificateParser {

    override fun parseCertificatePem(pem: String): X509Certificate {
        val result = SignumCertificate.decodeFromPem(pem)
        if (result.isFailure) {
            throw IllegalArgumentException(
                "Failed to parse certificate pem '${pem}'",
                result.exceptionOrNull()
            )
        }
        return createSignumX509Certificate(result.getOrThrow())
    }

    override fun parseCertificateDerEncoded(derEncoded: ByteString): X509Certificate = try {
        val signumCert = SignumCertificate.decodeFromDer(derEncoded.toByteArray())
        return createSignumX509Certificate(signumCert)
    } catch (error: Asn1Exception) {
        // Signum's parse failures extend Throwable directly; keep the provider detail here.
        throw IllegalArgumentException("Invalid DER certificate", error)
    }

    private fun createSignumX509Certificate(signumCert: SignumCertificate): SignumX509Certificate {
        //check it here and not with validator to have same behavior as BouncyCastle implementation
        require(signumCert.tbsCertificate.signatureAlgorithm.oid == signumCert.signatureAlgorithm.oid) {
            "Signature algorithm in TBS (${signumCert.tbsCertificate.signatureAlgorithm.oid}) not equal to signature algoritm in certificate (${signumCert.signatureAlgorithm.oid})"
        }
        return SignumX509Certificate(signumCert)
    }
}