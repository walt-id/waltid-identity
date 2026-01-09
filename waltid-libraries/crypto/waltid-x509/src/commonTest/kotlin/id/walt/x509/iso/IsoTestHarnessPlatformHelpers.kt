package id.walt.x509.iso

import okio.ByteString

expect fun isBigIntegerZero(
    bigInt: ByteString,
): Boolean

expect fun isBigIntegerPositive(
    bigInt: ByteString,
): Boolean