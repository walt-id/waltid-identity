package id.walt.crypto.keys

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

}
