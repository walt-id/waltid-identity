package id.walt.x509.iso

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import kotlinx.io.bytestring.ByteString

actual fun isBigIntegerZero(bigInt: ByteString): Boolean {
    TODO("Not yet implemented")
}

actual fun isBigIntegerPositive(bigInt: ByteString): Boolean {
    TODO("Not yet implemented")
}

actual suspend fun createIsoTestKey(
    keyType: KeyType,
    hasPrivateKey: Boolean,
): Key {
    TODO("Not yet implemented")
}
