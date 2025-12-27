package id.walt.x509

import id.walt.crypto.keys.Key

internal interface X509CertificateHandle {

    fun getCertificateDer(): CertificateDer

    suspend fun verifySignature(verificationKey: Key)
}