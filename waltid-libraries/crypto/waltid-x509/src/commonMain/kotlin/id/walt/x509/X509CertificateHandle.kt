package id.walt.x509

import id.walt.crypto.keys.Key

/**
 * Internal abstraction over platform X.509 certificate types.
 */
internal interface X509CertificateHandle {

    /**
     * Return the raw DER-encoded X.509 certificate bytes.
     */
    fun getCertificateDer(): CertificateDer

    /**
     * Verify the certificate signature using the provided key.
     *
     * @param verificationKey Public key expected to verify the X.509 certificate's signature.
     */
    suspend fun verifySignature(verificationKey: Key)
}
