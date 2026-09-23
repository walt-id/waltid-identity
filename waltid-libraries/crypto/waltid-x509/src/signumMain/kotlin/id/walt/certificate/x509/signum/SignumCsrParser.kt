package id.walt.certificate.x509.signum

import id.walt.certificate.x509.Pkcs10CertificateSigningRequest
import id.walt.certificate.x509.Pkcs10CertificateSigningRequestParser
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest as SignumCertificateRequest

class SignumCsrParser : Pkcs10CertificateSigningRequestParser {

    override fun parseCertificateSigningRequestPem(pem: String): Pkcs10CertificateSigningRequest {
        val result = SignumCertificateRequest.decodeFromPem(pem)
        if (result.isFailure) {
            throw IllegalArgumentException(
                "Failed to parse Pkcs10 Certification Request pem '${pem}'",
                result.exceptionOrNull()
            )
        }
        return SignumCsr(result.getOrThrow())
    }
}