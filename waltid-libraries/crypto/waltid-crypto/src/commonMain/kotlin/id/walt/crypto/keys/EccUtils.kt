package id.walt.crypto.keys

import kotlin.experimental.and

object EccUtils {


    /**
     * Primitive helper function to convert DER encoded
     * signature formats to IEEE P1363 encoded formats,
     * e.g., for use in JWTs
     *
     * for EC: Secp256r1 & Secp256k1
     */

    fun convertDERtoIEEEP1363(derSignature: ByteArray): ByteArray {
        // Assuming the signature starts with a DER sequence (0x30) followed by the length (which we skip)
        var index = 2 // Skipping the sequence byte and the length of the sequence

        fun ByteArray.trimLeadingZeroes(): ByteArray = this.dropWhile { it == 0x00.toByte() }.toByteArray()

        // Function to parse an integer (DER format starts with 0x02 followed by length)
        fun parseInteger(): ByteArray {
            if (derSignature[index] != 0x02.toByte()) throw IllegalArgumentException("Expected integer")
            index++ // Skip the integer marker
            val length = derSignature[index++].toInt() // Next byte is the length
            val integer = derSignature.copyOfRange(index, index + length)
            index += length
            return integer
        }

        // Parse r and s integers
        val r = parseInteger().trimLeadingZeroes()
        val s = parseInteger().trimLeadingZeroes()

        // Convert to fixed-length (32 bytes for each integer)
        val fixedLengthR = ByteArray(32)
        val fixedLengthS = ByteArray(32)
        r.copyInto(fixedLengthR, 32 - r.size)
        s.copyInto(fixedLengthS, 32 - s.size)

        // Concatenate r and s
        return fixedLengthR + fixedLengthS
    }

    fun convertDERtoIEEEP1363HandleExtra(derSignature: ByteArray): ByteArray {
        fun parseLength(bytes: ByteArray, indexAccessor: () -> Int): Int {
            val lengthByte = bytes[indexAccessor()].toInt() and 0xFF
            if (lengthByte < 128) return lengthByte

            val numLengthBytes = lengthByte - 128
            if (numLengthBytes > 4) throw IllegalArgumentException("Unsupported length for DER parsing")

            var length = 0
            repeat(numLengthBytes) {
                length = length shl 8
                length = length or (bytes[indexAccessor()].toInt() and 0xFF)
            }
            return length
        }

        fun ByteArray.trimLeadingZero() = if (this.isNotEmpty() && this[0] == 0.toByte()) this.drop(1).toByteArray() else this

        fun parseDerInteger(bytes: ByteArray, indexAccessor: () -> Int): ByteArray {
            if (bytes[indexAccessor()] != 0x02.toByte()) throw IllegalArgumentException("Expected integer marker")
            val length = parseLength(bytes, indexAccessor)
            return bytes.copyOfRange(indexAccessor(), indexAccessor() + length).trimLeadingZero()
        }

        var index = 0

        // Ensure the signature starts with a DER sequence marker (0x30)
        if (derSignature[index++] != 0x30.toByte()) throw IllegalArgumentException("Not a DER sequence")

        // Skip the sequence length
        val sequenceLength = parseLength(derSignature) { index++ }

        // Validate the total length
        if (sequenceLength + index != derSignature.size) throw IllegalArgumentException("Length mismatch in DER sequence")

        // Parse r and s integers
        val r = parseDerInteger(derSignature) { index++ }.trimLeadingZero()
        val s = parseDerInteger(derSignature) { index++ }.trimLeadingZero()

        // Ensure r and s are of expected length, padding if necessary
        val fixedLengthR = ByteArray(32) { if (it < 32 - r.size) 0 else r[it + r.size - 32] }
        val fixedLengthS = ByteArray(32) { if (it < 32 - s.size) 0 else s[it + s.size - 32] }

        // Concatenate r and s
        return fixedLengthR + fixedLengthS
    }


    fun convertP1363toDER(p1363Signature: ByteArray): ByteArray {
        val keySize = p1363Signature.size / 2
        if (p1363Signature.size % 2 != 0 || keySize == 0) {
            throw IllegalArgumentException("Invalid P1363 signature format")
        }

        // Split P1363 signature into r and s values
        val r = p1363Signature.sliceArray(0 until keySize)
        val s = p1363Signature.sliceArray(keySize until p1363Signature.size)

        // Convert r and s to ASN.1 integer encoding
        val encodedR = encodeAsASN1Integer(r)
        val encodedS = encodeAsASN1Integer(s)

        // Combine r and s into a DER SEQUENCE
        val sequenceLength = encodedR.size + encodedS.size
        val der = mutableListOf<Byte>()

        // DER Sequence: 0x30 [length] [encodedR] [encodedS]
        der.add(0x30) // Sequence tag
        der.add(sequenceLength.toByte()) // Length of the sequence
        der.addAll(encodedR.toList()) // Add r
        der.addAll(encodedS.toList()) // Add s

        return der.toByteArray()
    }

    // Helper function to encode a byte array as ASN.1 INTEGER
    private fun encodeAsASN1Integer(value: ByteArray): ByteArray {
        val mutableValue = value.toMutableList()

        // If the most significant bit of the first byte is set, prepend a 0x00 byte to avoid interpretation as negative
        if (mutableValue[0] and 0x80.toByte() != 0.toByte()) {
            mutableValue.add(0, 0x00)
        }

        val length = mutableValue.size
        val asn1Integer = mutableListOf<Byte>()

        // ASN.1 Integer: 0x02 [length] [value]
        asn1Integer.add(0x02) // Integer tag
        asn1Integer.add(length.toByte()) // Length of the integer
        asn1Integer.addAll(mutableValue) // The value itself

        return asn1Integer.toByteArray()
    }
}
