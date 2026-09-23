package id.walt.certificate.x509

import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.toHexString
import kotlin.test.assertFalse
import kotlin.test.assertTrue

object CertificateAssertionUtil {

    val ByteString.bitLength
        get() = this.size * 8

    fun assertBuildersSerialNoCompliance(
        serialNo: ByteString,
    ) {

        assertTrue(
            isBigIntegerPositive(serialNo),
            "Serial number must be positive but is ${serialNo.toHexString()}"
        )

        assertFalse(isBigIntegerZero(serialNo), "Serial number must not be zero")

        assertTrue(serialNo.bitLength <= 160, "Serial number must not exceed 20 bytes (160 bits)")

        assertTrue(serialNo.bitLength >= 63, "Serial number must contain at least 63 bits of entropy")

    }


    fun isBigIntegerZero(bigInt: ByteString): Boolean {
        return bigInt.toByteArray().all { it == 0.toByte() }
    }

    fun isBigIntegerPositive(bigInt: ByteString): Boolean {
        val bytes = bigInt.toByteArray()
        if (bytes.isEmpty()) return false
        return (bytes[0].toInt() and 0x80) == 0 && bytes.any { it != 0.toByte() }
    }
}