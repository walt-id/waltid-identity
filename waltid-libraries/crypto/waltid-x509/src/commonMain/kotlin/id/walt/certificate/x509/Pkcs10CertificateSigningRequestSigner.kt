package id.walt.certificate.x509

import id.walt.certificate.x509.builder.Pkcs10CertificateSigningRequestBuilder
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.Key
import id.walt.crypto.keys.Key as Crypto1Key

interface Pkcs10CertificateSigningRequestSigner {

    suspend fun signCsr(
        holderKey: Key,
        signatureAlgorithm: SignatureAlgorithm,
        csrBuilder: Pkcs10CertificateSigningRequestBuilder
    ): Pkcs10CertificateSigningRequest

    @Deprecated("Use crypto2 key method instead")
    suspend fun signCsr(
        holderKey: Crypto1Key,
        csrBuilder: Pkcs10CertificateSigningRequestBuilder
    ): Pkcs10CertificateSigningRequest
}