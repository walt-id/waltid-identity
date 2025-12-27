package id.walt.x509.id.walt.x509

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.parsePEMEncodedJcaPublicKey
import id.walt.x509.CertificateDer
import id.walt.x509.X509CertificateHandle
import java.security.cert.X509Certificate

data class JcaX509CertificateHandle(
    val certificate: X509Certificate,
) : X509CertificateHandle {

    override fun getCertificateDer() = CertificateDer(certificate.encoded)

    override suspend fun verifySignature(verificationKey: Key) {
        require(!verificationKey.hasPrivateKey) {
            "Verification key must be a public key, input key hasPrivateKey: ${verificationKey.hasPrivateKey}"
        }
        certificate.verify(parsePEMEncodedJcaPublicKey(verificationKey.exportPEM()))
    }

}