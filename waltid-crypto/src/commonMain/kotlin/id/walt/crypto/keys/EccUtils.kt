package id.walt.crypto.keys

object EccUtils {

    private fun ByteArray.padStart(length: Int): ByteArray {
        if (this.size >= length) return this
        val padded = ByteArray(length)
        this.copyInto(padded, length - this.size)
        return padded
    }

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

}
