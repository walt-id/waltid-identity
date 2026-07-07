package id.walt.certificate.x509

interface X509CertificateServices {
    val certificateParser: X509CertificateParser
    val csrParser: Pkcs10CertificateSigningRequestParser
    val csrSigner: Pkcs10CertificateSigningRequestSigner

    val serialNumberGenerator: X509CertificateSerialNumberGenerator
    val certificateSigner: X509CertificateSigner
}