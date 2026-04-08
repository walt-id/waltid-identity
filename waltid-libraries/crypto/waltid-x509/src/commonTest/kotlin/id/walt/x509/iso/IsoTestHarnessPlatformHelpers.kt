package id.walt.x509.iso

import kotlinx.io.bytestring.ByteString

expect fun isBigIntegerZero(
    bigInt: ByteString,
): Boolean

expect fun isBigIntegerPositive(
    bigInt: ByteString,
): Boolean
