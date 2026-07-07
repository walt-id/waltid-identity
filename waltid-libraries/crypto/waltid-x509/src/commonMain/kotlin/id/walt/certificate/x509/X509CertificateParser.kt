package id.walt.certificate.x509

interface X509CertificateParser {
    fun parseCertificatePem(pem: String): X509Certificate
}