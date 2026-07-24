package id.walt.certificate.x509

import id.walt.certificate.x509.validation.X509CertificateChainValidator

class X509CertificateServices(
    val certificateParser: X509CertificateParser,
    val csrParser: Pkcs10CertificateSigningRequestParser,
    val csrSigner: Pkcs10CertificateSigningRequestSigner,
    val serialNumberGenerator: X509CertificateSerialNumberGenerator,
    val certificateSigner: X509CertificateSigner,
    val certificateChainValidator: X509CertificateChainValidator
) {
    fun copy(
        certificateParser: X509CertificateParser? = null,
        csrParser: Pkcs10CertificateSigningRequestParser? = null,
        csrSigner: Pkcs10CertificateSigningRequestSigner? = null,
        serialNumberGenerator: X509CertificateSerialNumberGenerator? = null,
        certificateSigner: X509CertificateSigner? = null,
        certificateChainValidator: X509CertificateChainValidator? = null
    ): X509CertificateServices {
        return X509CertificateServices(
            certificateParser ?: this.certificateParser,
            csrParser ?: this.csrParser,
            csrSigner ?: this.csrSigner,
            serialNumberGenerator ?: this.serialNumberGenerator,
            certificateSigner ?: this.certificateSigner,
            certificateChainValidator ?: this.certificateChainValidator
        )
    }
}