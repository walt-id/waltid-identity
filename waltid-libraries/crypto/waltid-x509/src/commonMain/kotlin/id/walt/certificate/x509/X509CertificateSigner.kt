package id.walt.certificate.x509

import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.crypto.keys.Key

interface X509CertificateSigner {

    fun signCertificate(
        issuerKey: Key,
        builder: X509CertificateDataBuilder
    ): X509Certificate

    fun validateCertificateSignature(
        issuerPublicKey: X509Certificate.SubjectPublicKeyInfo,
        certificate: X509Certificate
    ): Boolean
}