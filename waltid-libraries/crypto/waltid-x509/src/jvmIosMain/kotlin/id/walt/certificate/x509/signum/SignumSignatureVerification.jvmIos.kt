package id.walt.certificate.x509.signum

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.CryptoSignature
import at.asitplus.signum.indispensable.SignatureAlgorithm
import at.asitplus.signum.supreme.sign.InvalidSignature
import at.asitplus.signum.supreme.sign.verifierFor
import at.asitplus.signum.supreme.sign.verify

internal actual suspend fun verifySignumSignature(
    publicKey: CryptoPublicKey,
    algorithm: SignatureAlgorithm,
    signedData: ByteArray,
    signature: CryptoSignature,
): Boolean {
    val verifier = algorithm.verifierFor(publicKey).getOrThrow()
    return verifier.verify(signedData, signature).fold(
        onSuccess = { true },
        onFailure = { cause ->
            when (cause) {
                is InvalidSignature -> false
                else -> throw cause
            }
        },
    )
}
