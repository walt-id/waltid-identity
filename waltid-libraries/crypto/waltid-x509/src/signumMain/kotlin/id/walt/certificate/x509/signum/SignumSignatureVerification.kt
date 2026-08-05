package id.walt.certificate.x509.signum

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.CryptoSignature
import at.asitplus.signum.indispensable.SignatureAlgorithm

internal expect suspend fun verifySignumSignature(
    publicKey: CryptoPublicKey,
    algorithm: SignatureAlgorithm,
    signedData: ByteArray,
    signature: CryptoSignature,
): Boolean
