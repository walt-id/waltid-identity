package id.walt.certificate.x509

import id.walt.certificate.x509.validation.X509CertificateChainValidator
import id.walt.certificate.x509.validation.validator.X509CertificateSignatureValidator
import id.walt.certificate.x509.validation.validator.X509CertificateValidityValidator
import id.walt.x509.id.walt.certificate.x509.JavaX509CertificateSerialNumberGenerator
import id.walt.certificate.x509.bouncycastle.BouncyPkcs10CertificateSigningRequestParser
import id.walt.certificate.x509.bouncycastle.BouncyPkcs10CertificateSigningRequestSigner
import id.walt.certificate.x509.bouncycastle.BouncyX509CertificateParser
import id.walt.certificate.x509.bouncycastle.BouncyX509CertificateSigner
import id.walt.certificate.x509.validation.validator.X509CertificateBasicConstraintsValidator
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.x509.id.walt.certificate.x509.javasec.JavaDefaultTrustStore

actual fun platformDefaultServices(): X509CertificateServices {
    val certificateParser = BouncyX509CertificateParser()
    val certificateSigner = BouncyX509CertificateSigner()
    return X509CertificateServices(
        cryptoRuntime = CryptoRuntime(defaultSoftwareKeyProviders()),
        csrParser = BouncyPkcs10CertificateSigningRequestParser(),
        csrSigner = BouncyPkcs10CertificateSigningRequestSigner(),
        certificateParser = certificateParser,
        signatureValidator = certificateSigner,
        serialNumberGenerator = JavaX509CertificateSerialNumberGenerator(),
        certificateSigner = certificateSigner,
        certificateChainValidator = X509CertificateChainValidator(
            listOf(
                X509CertificateValidityValidator(),
                X509CertificateBasicConstraintsValidator(),
                X509CertificateSignatureValidator(certificateSigner)
            ),
            JavaDefaultTrustStore(certificateParser)
        )
    )
}