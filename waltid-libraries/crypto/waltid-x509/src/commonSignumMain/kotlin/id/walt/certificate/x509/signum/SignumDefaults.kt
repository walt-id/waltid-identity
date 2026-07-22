package id.walt.certificate.x509.signum

import id.walt.certificate.x509.*
import id.walt.certificate.x509.validation.X509CertificateChainValidator

class SignumDefaults(
    override val serialNumberGenerator: X509CertificateSerialNumberGenerator
) : X509CertificateServices {
    override val certificateParser: X509CertificateParser = SignumCertificateParser()

    override val csrParser: Pkcs10CertificateSigningRequestParser = SignumCsrParser()

    override val csrSigner: Pkcs10CertificateSigningRequestSigner = SignumCsrSigner()

    override val certificateSigner: X509CertificateSigner
        get() = TODO("Not yet implemented")

    override val certificateChainValidator: X509CertificateChainValidator
        get() = TODO("Not yet implemented")
}