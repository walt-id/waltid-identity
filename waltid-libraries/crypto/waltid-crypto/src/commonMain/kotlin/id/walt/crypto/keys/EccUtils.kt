package id.walt.crypto.keys

object EccUtils {

    /**
     * P-256/k1 (64), P-384 (96), and P-521 (132)
     */
    private val p1363Lengths = setOf(64, 96, 132)

    /**
     * Converts a DER-encoded ECDSA signature to the IEEE P1363 format (raw R||S).
     *
     * This function is designed to work with common elliptic curves used in JWTs,
     * including secp256r1, secp256k1, secp384r1, and secp521r1.
     *
     * @param derSignature The signature encoded in DER format.
     * @return The signature in IEEE P1363 format (a simple concatenation of R and S).
     * @throws IllegalArgumentException if the signature is not a valid DER sequence or if the
     * component sizes are unsupported.
     */
    fun convertDERtoIEEEP1363(derSignature: ByteArray): ByteArray {
        // A DER-encoded signature is an ASN.1 SEQUENCE.
        // It must start with 0x30.
        if (derSignature.isEmpty() || derSignature[0] != 0x30.toByte()) {
            // Passed data might already be in IEEE P1363 format, or it might just be invalid.

            if (derSignature.size in p1363Lengths) {
                // Signature is already in IEEE P1363 format
                return derSignature
            }

            throw IllegalArgumentException("Signature is not a valid DER sequence.")
        }

        // --- Correctly skip the DER sequence header ---
        // The byte after 0x30 indicates the length of the rest of the sequence.
        var index = 1 // Start after the 0x30 marker

        // Read the sequence length. DER supports short-form (< 128 bytes) and long-form lengths.
        // For secp521r1, the signature is long enough to require the long-form encoding.
        var seqLen = derSignature[index++].toInt() and 0xFF
        if (seqLen and 0x80 != 0) { // Check if the long-form bit is set.
            val lenBytes = seqLen and 0x7F // The lower 7 bits tell us how many bytes follow for the length.
            // We just need to skip these length bytes to get to the content.
            index += lenBytes
        }
        // `index` now points to the start of the 'r' integer component.

        // --- Helper function to trim leading zeros from a byte array ---
        // This is necessary because DER integers are signed and may be padded with a
        // leading 0x00 byte to ensure they are interpreted as positive numbers.
        fun ByteArray.trimLeadingZeroes(): ByteArray = this.dropWhile { it == 0x00.toByte() }.toByteArray()

        // --- Helper function to parse a DER-encoded integer ---
        fun parseInteger(): ByteArray {
            // Check for the INTEGER marker (0x02).
            if (index >= derSignature.size || derSignature[index] != 0x02.toByte()) {
                throw IllegalArgumentException("Expected integer marker (0x02) at index $index")
            }
            index++ // Skip the integer marker

            if (index >= derSignature.size) {
                throw IllegalArgumentException("Unexpected end of signature after integer marker.")
            }

            // Get the length of the integer value. For r and s in the specified curves,
            // this will be a single byte (short-form).
            val length = derSignature[index++].toInt() and 0xFF

            if (index + length > derSignature.size) {
                throw IllegalArgumentException("Declared integer length ($length) is out of bounds.")
            }

            // Copy the integer value and advance the index.
            val integer = derSignature.copyOfRange(index, index + length)
            index += length
            return integer
        }

        // --- Main Logic ---

        // 1. Parse r and s integers from the DER sequence.
        val r = parseInteger().trimLeadingZeroes()
        val s = parseInteger().trimLeadingZeroes()

        // 2. Determine the expected key size for padding. Instead of being hardcoded to 32,
        // we infer it from the size of the parsed r and s values.
        val maxLen = maxOf(r.size, s.size)
        val keySizeBytes = when {
            maxLen <= 32 -> 32 // For secp256r1, secp256k1
            maxLen <= 48 -> 48 // For secp384r1
            maxLen <= 66 -> 66 // For secp521r1
            else -> throw IllegalArgumentException("Unsupported signature component size: $maxLen bytes")
        }

        // 3. Create fixed-length byte arrays for the P1363 format.
        val fixedLengthR = ByteArray(keySizeBytes)
        val fixedLengthS = ByteArray(keySizeBytes)

        // 4. Copy r and s into the fixed-length arrays. This effectively pads them with
        // leading zeros to match the required length for the curve.
        // The destination index is calculated to right-align the value.
        r.copyInto(destination = fixedLengthR, destinationOffset = keySizeBytes - r.size)
        s.copyInto(destination = fixedLengthS, destinationOffset = keySizeBytes - s.size)

        // 5. Concatenate r and s to form the final IEEE P1363 signature.
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

    /**
     * Encodes a raw byte array as a DER-encoded ASN.1 INTEGER.
     *
     * This involves prepending the 0x02 tag, the length, and a leading 0x00 byte
     * if the most significant bit of the value is set (to ensure it's interpreted as positive).
     * It also trims unnecessary leading zeros from the input value.
     *
     * @param value The raw integer value (e.g., the 'r' or 's' component of a signature).
     * @return The DER-encoded integer as a ByteArray.
     */
    private fun encodeAsASN1Integer(value: ByteArray): ByteArray {
        // 1. Trim any leading zeros from the input, as they are not part of the value itself.
        var trimmedValue = value.dropWhile { it == 0.toByte() }.toByteArray()
        if (trimmedValue.isEmpty()) {
            trimmedValue = byteArrayOf(0) // Handle case where the original value was 0.
        }

        // 2. Check if the most significant bit is set. If so, a leading 0x00 is required
        // to ensure the number is interpreted as positive.
        val needsZeroPrefix = (trimmedValue[0].toInt() and 0x80) != 0
        val integerBytes = if (needsZeroPrefix) {
            byteArrayOf(0x00) + trimmedValue
        } else {
            trimmedValue
        }

        // 3. Construct the final DER integer: 0x02 (tag) + length + value
        // The length must also be encoded correctly (which for an integer component is always short-form).
        return byteArrayOf(0x02, integerBytes.size.toByte()) + integerBytes
    }


    /**
     * Converts an IEEE P1363 formatted signature (raw R||S) to the DER format.
     *
     * This function is designed to work with common elliptic curves, including
     * secp256r1, secp256k1, secp384r1, and secp521r1.
     *
     * @param p1363Signature The signature in P1363 format.
     * @return The signature in DER format.
     * @throws IllegalArgumentException if the P1363 signature format is invalid.
     */
    fun convertP1363toDER(p1363Signature: ByteArray): ByteArray {
        // P1363 is a simple concatenation of R and S, which should be of equal length.
        val keySize = p1363Signature.size / 2
        if (p1363Signature.size % 2 != 0 || keySize == 0) {
            throw IllegalArgumentException("Invalid P1363 signature format: size must be even and non-zero.")
        }

        // 1. Split the P1363 signature into its 'r' and 's' components.
        val r = p1363Signature.sliceArray(0 until keySize)
        val s = p1363Signature.sliceArray(keySize until p1363Signature.size)

        // 2. Convert r and s to their ASN.1 INTEGER representation.
        val encodedR = encodeAsASN1Integer(r)
        val encodedS = encodeAsASN1Integer(s)

        // 3. Combine r and s into the content of the DER SEQUENCE.
        val sequenceContent = encodedR + encodedS
        val sequenceLength = sequenceContent.size

        val der = mutableListOf<Byte>()

        // Add the SEQUENCE tag (0x30).
        der.add(0x30)

        // 4. Correctly encode the sequence length based on its size.
        if (sequenceLength < 128) {
            // Use the short-form for lengths under 128.
            der.add(sequenceLength.toByte())
        } else {
            // Use the long-form for lengths 128 or greater.
            // First, determine how many bytes are needed to represent the length.
            val lengthBytes = when {
                sequenceLength < 0x100 -> byteArrayOf(sequenceLength.toByte()) // Fits in 1 byte
                sequenceLength < 0x10000 -> byteArrayOf((sequenceLength shr 8).toByte(), sequenceLength.toByte()) // Fits in 2 bytes
                else -> throw IllegalArgumentException("Signature too large to be encoded.")
            }
            // The first length octet is 0x80 OR-ed with the number of subsequent length octets.
            der.add((0x80 or lengthBytes.size).toByte())
            der.addAll(lengthBytes.toList())
        }

        // 5. Add the actual content (the encoded r and s values).
        der.addAll(sequenceContent.toList())

        return der.toByteArray()
    }
}
