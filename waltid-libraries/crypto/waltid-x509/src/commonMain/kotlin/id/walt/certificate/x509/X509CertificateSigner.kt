package id.walt.certificate.x509

import id.walt.certificate.x509.builder.X509CertificateDataBuilder
import id.walt.crypto2.keys.Key
import id.walt.crypto.keys.Key as Crypto1Key

interface X509CertificateSigner {

    suspend fun signCertificate(
        issuerKey: Key,
        builder: X509CertificateDataBuilder
    ): X509Certificate

    suspend fun signCertificate(
        issuerKey: Crypto1Key,
        builder: X509CertificateDataBuilder
    ): X509Certificate

}