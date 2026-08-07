package id.walt.certificate.x509

import id.walt.certificate.x509.nodejs.NodejsX509CertificateSerialNumberGenerator
import id.walt.certificate.x509.signum.*
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.X509CertificateChainValidator
import id.walt.certificate.x509.validation.validator.X509CertificateSignatureValidator
import id.walt.certificate.x509.validation.validator.X509CertificateValidityValidator

actual fun platformDefaultServices(): X509CertificateServices {
    val signatureValidator = SignumSignatureValidator()
    return X509CertificateServices(
        csrParser = SignumCsrParser(),
        csrSigner = SignumCsrSigner(),
        certificateParser = SignumCertificateParser(),
        signatureValidator = signatureValidator,
        serialNumberGenerator = NodejsX509CertificateSerialNumberGenerator(),
        certificateSigner = SignumCertificateSigner(),
        certificateChainValidator = X509CertificateChainValidator(
            listOf(
                X509CertificateValidityValidator(),
                X509CertificateSignatureValidator(signatureValidator)
            ),
            // TODO: Implement Node.js system trust store
            InMemoryTrustStore()
        )
    )
}