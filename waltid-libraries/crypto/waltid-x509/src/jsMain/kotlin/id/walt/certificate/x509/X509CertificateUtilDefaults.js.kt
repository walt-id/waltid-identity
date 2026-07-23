package id.walt.certificate.x509

import id.walt.certificate.x509.nodejs.NodejsX509CertificateSerialNumberGenerator
import id.walt.certificate.x509.signum.SignumCertificateParser
import id.walt.certificate.x509.signum.SignumCertificateSigner
import id.walt.certificate.x509.signum.SignumCsrParser
import id.walt.certificate.x509.signum.SignumCsrSigner
import id.walt.certificate.x509.signum.SignumSignatureValidator
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.X509CertificateChainValidator
import id.walt.certificate.x509.validation.validator.X509CertificateSignatureValidator
import id.walt.certificate.x509.validation.validator.X509CertificateValidityValidator

actual object X509CertificateUtilDefaults : X509CertificateServices {

    actual override val csrParser: Pkcs10CertificateSigningRequestParser =
        SignumCsrParser()

    actual override val csrSigner: Pkcs10CertificateSigningRequestSigner =
        SignumCsrSigner()

    actual override val certificateParser: X509CertificateParser =
        SignumCertificateParser()

    actual override val serialNumberGenerator: X509CertificateSerialNumberGenerator =
        NodejsX509CertificateSerialNumberGenerator()

    actual override val certificateSigner: X509CertificateSigner =
        SignumCertificateSigner()

    actual override val certificateChainValidator: X509CertificateChainValidator =
        X509CertificateChainValidator(
            listOf(
                X509CertificateValidityValidator(),
                X509CertificateSignatureValidator(SignumSignatureValidator())
            ),
            InMemoryTrustStore()
        )
}