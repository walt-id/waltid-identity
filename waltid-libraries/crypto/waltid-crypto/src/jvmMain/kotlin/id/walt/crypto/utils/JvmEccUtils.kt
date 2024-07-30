package id.walt.crypto.utils

import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import java.math.BigInteger

object JvmEccUtils {


    private fun ByteArray.padStart(length: Int, padByte: Byte = 0): ByteArray {
        if (this.size > length) return this

        val padSize = length - this.size
        val padded = ByteArray(length)

        System.arraycopy(this, 0, padded, padSize, this.size)
        if (padSize > 0) padded.fill(padByte, 0, padSize)

        return padded
    }

    fun convertDERtoIEEEP1363BouncyCastle(derSignature: ByteArray): ByteArray {
        val asn1InputStream = ASN1InputStream(derSignature)
        val asn1Sequence = asn1InputStream.readObject() as ASN1Sequence

        val r = (asn1Sequence.getObjectAt(0) as ASN1Integer).positiveValue
        val s = (asn1Sequence.getObjectAt(1) as ASN1Integer).positiveValue

        val rBytes = r.toByteArray().let {
            if (it.size > 32 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it
        }.padStart(32, 0)

        val sBytes = s.toByteArray().let {
            if (it.size > 32 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it
        }.padStart(32, 0)

        return rBytes + sBytes
    }

    /**
     * 1. parse DER-encoded signature with `parseDerSignature` function, to extract `r` and `s` components (as BigInteger values)
     * 2. `r` and `s` values are converted to fixed-length byte arrays of 32 bytes each. If the byte arrays are shorter than 32 bytes, they are padded with leading zeros
     * 3. Fixed length byte arrays `r` and `s` are concatenated to form the IEEE P1363 signature format
     * 4. The resulting IEEE P1363 signature is returned as ByteArray
     *
     * Note: only use for DER-encoded signatures that follow ASN.1 structure **for ECDSA signatures**
     */
    fun convertDERtoIEEEP1363Test2(derSignature: ByteArray): ByteArray {
        fun parseDerSignature(derSignature: ByteArray): Pair<BigInteger, BigInteger> {
            var offset = 0

            // Check if signature starts with the correct sequence identifier
            if (derSignature[offset++] != 0x30.toByte()) {
                throw IllegalArgumentException("Invalid DER signature format: Invalid sequence identifier")
            }

            // Parse the length of the sequence
            var length = derSignature[offset++].toInt()
            if (length and 0x80 != 0) {
                val lengthBytes = length and 0x7F
                length = 0
                for (i in 0 until lengthBytes) {
                    length = length shl 8 or (derSignature[offset++].toInt() and 0xFF)
                }
            }

            fun parseInteger(): BigInteger {
                if (derSignature[offset++] != 0x02.toByte()) {
                    throw IllegalArgumentException("Invalid DER signature format: Invalid integer identifier for")
                }
                val intLength = derSignature[offset++].toInt()
                val intBytes = derSignature.copyOfRange(offset, offset + intLength)
                val int = BigInteger(1, intBytes)
                offset += intLength

                return int
            }

            // Parse the ASN.1 DER integer 'r'
            val r = parseInteger()

            // Parse the ASN.1 DER integer 's'
            val s = parseInteger()

            return Pair(r, s)
        }

        // Parse DER-encoded signature
        val (r, s) = parseDerSignature(derSignature)

        // Convert r and s to fixed-length byte arrays
        val rBytes = r.toByteArray().padStart(32, 0)
        val sBytes = s.toByteArray().padStart(32, 0)

        // Concatenate r and s to form the IEEE P1363 signature
        return rBytes + sBytes
    }

}
