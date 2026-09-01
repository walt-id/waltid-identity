package id.walt.certificate.x509

import id.walt.certificate.x509.nodejs.NodejsX509CertificateSerialNumberGenerator
import id.walt.certificate.x509.signum.*
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.certificate.x509.validation.X509CertificateChainValidator
import id.walt.certificate.x509.validation.validator.X509CertificateBasicConstraintsValidator
import id.walt.certificate.x509.validation.validator.X509CertificateSignatureValidator
import id.walt.certificate.x509.validation.validator.X509CertificateValidityValidator
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders

actual fun platformDefaultServices(): X509CertificateServices {
    val signatureValidator = SignumSignatureValidator(
        SignumCrypto2SignatureValidationImpl()
    )
    val signer = SignumCertificateSigner()
    return X509CertificateServices(
        cryptoRuntime = CryptoRuntime(defaultSoftwareKeyProviders()),
        csrParser = SignumCsrParser(),
        csrSigner = signer,
        certificateParser = SignumCertificateParser(),
        signatureValidator = signatureValidator,
        serialNumberGenerator = NodejsX509CertificateSerialNumberGenerator(),
        certificateSigner = signer,
        certificateChainValidator = X509CertificateChainValidator(
            listOf(
                X509CertificateValidityValidator(),
                X509CertificateBasicConstraintsValidator(),
                X509CertificateSignatureValidator(signatureValidator)
            ),
            // TODO: Implement Node.js system trust store
            InMemoryTrustStore()
        )
    )
}