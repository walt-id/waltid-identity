package id.walt.x509

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.pki.X509Certificate
import at.asitplus.signum.indispensable.requireSupported
import at.asitplus.signum.supreme.sign.SignatureInput
import at.asitplus.signum.supreme.sign.verifierFor
import id.walt.crypto.keys.Key

internal data class SignumX509CertificateHandle(
    private val certificate: X509Certificate,
    private val certificateDer: CertificateDer,
) : X509CertificateHandle {

    override fun getCertificateDer(): CertificateDer = certificateDer

    override suspend fun verifySignature(verificationKey: Key) {
        require(!verificationKey.hasPrivateKey) {
            "Verification key must be a public key, input key hasPrivateKey: ${verificationKey.hasPrivateKey}"
        }

        val publicKey = CryptoPublicKey.decodeFromDer(
            verificationKey.getPublicKeyRepresentation()
        )
        val signatureAlgorithm = certificate.signatureAlgorithm
        signatureAlgorithm.requireSupported()
        val verifier = signatureAlgorithm.algorithm
            .verifierFor(publicKey)
            .getOrThrow()

        verifier.verify(
            data = SignatureInput(certificate.rawTbsCertificate.derEncoded),
            sig = certificate.decodedSignature.getOrThrow(),
        ).getOrThrow()
    }
}
