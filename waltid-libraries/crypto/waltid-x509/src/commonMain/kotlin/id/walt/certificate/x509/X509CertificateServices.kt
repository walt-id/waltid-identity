package id.walt.certificate.x509

import id.walt.certificate.x509.validation.X509CertificateChainValidator
import id.walt.crypto2.CryptoRuntime

class X509CertificateServices(
    val cryptoRuntime: CryptoRuntime,
    val certificateParser: X509CertificateParser,
    val csrParser: Pkcs10CertificateSigningRequestParser,
    val csrSigner: Pkcs10CertificateSigningRequestSigner,
    val signatureValidator: SignatureValidator,
    val serialNumberGenerator: X509CertificateSerialNumberGenerator,
    val certificateSigner: X509CertificateSigner,
    val certificateChainValidator: X509CertificateChainValidator
) {
    fun copy(
        cryptoRuntime: CryptoRuntime? = null,
        certificateParser: X509CertificateParser? = null,
        csrParser: Pkcs10CertificateSigningRequestParser? = null,
        csrSigner: Pkcs10CertificateSigningRequestSigner? = null,
        signatureValidator: SignatureValidator? = null,
        serialNumberGenerator: X509CertificateSerialNumberGenerator? = null,
        certificateSigner: X509CertificateSigner? = null,
        certificateChainValidator: X509CertificateChainValidator? = null
    ): X509CertificateServices {
        return X509CertificateServices(
            cryptoRuntime ?: this.cryptoRuntime,
            certificateParser ?: this.certificateParser,
            csrParser ?: this.csrParser,
            csrSigner ?: this.csrSigner,
            signatureValidator ?: this.signatureValidator,
            serialNumberGenerator ?: this.serialNumberGenerator,
            certificateSigner ?: this.certificateSigner,
            certificateChainValidator ?: this.certificateChainValidator
        )
    }
}