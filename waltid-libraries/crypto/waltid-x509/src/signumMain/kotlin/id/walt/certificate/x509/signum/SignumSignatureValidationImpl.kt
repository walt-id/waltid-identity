package id.walt.certificate.x509.signum

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.CryptoSignature
import at.asitplus.signum.indispensable.SignatureAlgorithm
import id.walt.crypto2.CryptoRuntime

interface SignumSignatureValidationImpl {
    suspend fun verifySignumSignature(
        cryptoRuntime: CryptoRuntime,
        publicKey: CryptoPublicKey,
        algorithm: SignatureAlgorithm,
        signedData: ByteArray,
        signature: CryptoSignature,
    ): Boolean
}