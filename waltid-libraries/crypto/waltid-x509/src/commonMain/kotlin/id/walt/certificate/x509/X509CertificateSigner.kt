package id.walt.certificate.x509

import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.crypto.keys.Key

interface X509CertificateSigner {

    suspend fun signCertificate(
        issuerKey: Key,
        builder: X509CertificateDataBuilder
    ): X509Certificate
}