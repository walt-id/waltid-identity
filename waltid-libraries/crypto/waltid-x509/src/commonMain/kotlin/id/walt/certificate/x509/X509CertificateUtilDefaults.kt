package id.walt.certificate.x509

import id.walt.certificate.x509.validation.X509CertificateChainValidator

expect object X509CertificateUtilDefaults : X509CertificateServices {
    override val csrParser: Pkcs10CertificateSigningRequestParser
    override val csrSigner: Pkcs10CertificateSigningRequestSigner

    override val serialNumberGenerator: X509CertificateSerialNumberGenerator
    override val certificateParser: X509CertificateParser
    override val certificateSigner: X509CertificateSigner
    override val certificateChainValidator: X509CertificateChainValidator
}