package id.walt.x509.iso

import kotlinx.io.bytestring.ByteString

actual fun isBigIntegerZero(bigInt: ByteString): Boolean {
    return bigInt.toByteArray().all { it == 0.toByte() }
}

actual fun isBigIntegerPositive(bigInt: ByteString): Boolean {
    val bytes = bigInt.toByteArray()
    if (bytes.isEmpty()) return false
    // Positive if sign bit is not set (two's complement)
    return (bytes[0].toInt() and 0x80) == 0 && bytes.any { it != 0.toByte() }
}
