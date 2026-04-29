package id.walt.x509

import id.walt.crypto.keys.Key
import id.walt.crypto.utils.parsePEMEncodedJcaPublicKey
import kotlinx.io.bytestring.ByteString
import java.security.cert.X509Certificate

internal data class JcaX509CertificateHandle(
    val certificate: X509Certificate,
) : X509CertificateHandle {

    override fun getCertificateDer() = CertificateDer(ByteString( certificate.encoded))

    override suspend fun verifySignature(verificationKey: Key) {
        require(!verificationKey.hasPrivateKey) {
            "Verification key must be a public key, input key hasPrivateKey: ${verificationKey.hasPrivateKey}"
        }
        certificate.verify(parsePEMEncodedJcaPublicKey(verificationKey.exportPEM()))
    }

}
