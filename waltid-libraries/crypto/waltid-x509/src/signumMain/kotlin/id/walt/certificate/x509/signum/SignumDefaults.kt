package id.walt.certificate.x509.signum

import id.walt.certificate.x509.*
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.X509CertificateChainValidator
import id.walt.certificate.x509.validation.validator.X509CertificateSignatureValidator
import id.walt.certificate.x509.validation.validator.X509CertificateValidityValidator

class SignumDefaults(
    override val serialNumberGenerator: X509CertificateSerialNumberGenerator,
    signatureValidator: id.walt.certificate.x509.SignatureValidator
) : X509CertificateServices {
    override val certificateParser: X509CertificateParser = SignumCertificateParser()

    override val csrParser: Pkcs10CertificateSigningRequestParser = SignumCsrParser()

    override val csrSigner: Pkcs10CertificateSigningRequestSigner = SignumCsrSigner()

    override val certificateSigner: X509CertificateSigner = SignumCertificateSigner()

    override val certificateChainValidator: X509CertificateChainValidator = X509CertificateChainValidator(
        listOf(X509CertificateValidityValidator(), X509CertificateSignatureValidator(signatureValidator)),
        InMemoryTrustStore()
    )
}