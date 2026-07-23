package id.walt.certificate.x509

import id.walt.certificate.x509.validation.X509CertificateChainValidator

class CustomX509CertificateServices private constructor(
    override val certificateParser: X509CertificateParser,
    override val csrParser: Pkcs10CertificateSigningRequestParser,
    override val csrSigner: Pkcs10CertificateSigningRequestSigner,
    override val serialNumberGenerator: X509CertificateSerialNumberGenerator,
    override val certificateSigner: X509CertificateSigner,
    override val certificateChainValidator: X509CertificateChainValidator
) : X509CertificateServices {

    companion object {
        fun X509CertificateServices.custom(
            trustStore: X509CertificateTrustStore? = null,
        ): X509CertificateServices =
            CustomX509CertificateServices(
                certificateParser,
                csrParser,
                csrSigner,
                serialNumberGenerator,
                certificateSigner,
                trustStore?.let {
                    X509CertificateChainValidator(
                        certificateChainValidator.validators,
                        it
                    )
                } ?: certificateChainValidator
            )
    }
}