package id.walt.certificate.x509

import id.walt.certificate.x509.builder.Pkcs10CertificateSigningRequestBuilder
import id.walt.crypto.keys.Key

interface Pkcs10CertificateSigningRequestSigner {
    suspend fun signCsr(holderKey: Key, csrBuilder: Pkcs10CertificateSigningRequestBuilder): Pkcs10CertificateSigningRequest
}