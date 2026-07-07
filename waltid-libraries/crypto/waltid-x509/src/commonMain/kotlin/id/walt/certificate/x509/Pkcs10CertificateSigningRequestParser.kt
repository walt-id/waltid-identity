package id.walt.certificate.x509

interface Pkcs10CertificateSigningRequestParser {
    fun parseCertificateSigningRequestPem(pem: String): Pkcs10CertificateSigningRequest
}