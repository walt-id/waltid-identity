package id.walt.x509.iso

import at.asitplus.signum.indispensable.CryptoPublicKey
import at.asitplus.signum.indispensable.CryptoSignature
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.parsePEMEncodedJcaPublicKey

internal actual suspend fun Key.toSignumPublicKey(): CryptoPublicKey =
    CryptoPublicKey.decodeFromDer(
        parsePEMEncodedJcaPublicKey(getPublicKey().exportPEM()).encoded
    )

internal actual suspend fun Key.signSignumX509Raw(data: ByteArray): CryptoSignature {
    val signatureBytes = signRaw(data) as? ByteArray
        ?: error("X.509 signing returned a non-ByteArray signature")
    return when (keyType) {
        KeyType.secp256r1, KeyType.secp384r1, KeyType.secp521r1 ->
            CryptoSignature.EC.decodeFromDer(signatureBytes)
        KeyType.RSA, KeyType.RSA3072, KeyType.RSA4096 ->
            CryptoSignature.RSA(signatureBytes)
        else -> error("Unsupported X.509 signing key type: $keyType")
    }
}
