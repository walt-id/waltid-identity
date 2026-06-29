package id.walt.x509.iso

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import kotlinx.io.bytestring.ByteString
import java.math.BigInteger

actual fun isBigIntegerZero(bigInt: ByteString): Boolean {
    return BigInteger(bigInt.toByteArray()) == BigInteger.ZERO
}

actual fun isBigIntegerPositive(bigInt: ByteString): Boolean {
    return BigInteger(bigInt.toByteArray()).signum() == 1
}

actual suspend fun createIsoTestKey(
    keyType: KeyType,
    hasPrivateKey: Boolean,
): Key {
    val key = KeyManager.createKey(
        KeyGenerationRequest(
            backend = "jwk",
            keyType = keyType,
        )
    )
    return if (hasPrivateKey) key else key.getPublicKey()
}
