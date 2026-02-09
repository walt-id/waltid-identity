package id.walt.x509.iso

import okio.ByteString
import java.math.BigInteger

actual fun isBigIntegerZero(bigInt: ByteString): Boolean {
    return BigInteger(bigInt.toByteArray()) == BigInteger.ZERO
}

actual fun isBigIntegerPositive(bigInt: ByteString): Boolean {
    return BigInteger(bigInt.toByteArray()).signum() == 1
}