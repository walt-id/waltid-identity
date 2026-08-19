package id.walt.certificate.x509

import id.walt.crypto2.CryptoRuntime

interface SignatureValidator {

    val name: String

    suspend fun validateCertificateSignature(
        cryptoRuntime: CryptoRuntime,
        issuerPublicKey: X509Certificate.SubjectPublicKeyInfo,
        certificate: X509Certificate
    ): Boolean

    suspend fun validateCsrSignature(
        cryptoRuntime: CryptoRuntime,
        csr: Pkcs10CertificateSigningRequest
    ): Boolean
}