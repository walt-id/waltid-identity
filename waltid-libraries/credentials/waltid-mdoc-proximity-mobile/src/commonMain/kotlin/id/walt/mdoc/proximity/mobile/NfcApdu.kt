package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ImmutableBytes

/** A validated ISO/IEC 7816-4 command APDU using short or extended length encoding. */
internal data class NfcCommandApdu(
    public val cla: UByte,
    public val instruction: UByte,
    public val parameter1: UByte,
    public val parameter2: UByte,
    public val data: ImmutableBytes = ImmutableBytes.of(byteArrayOf()),
    public val expectedResponseDataLength: Int? = null,
) {
    init {
        require(data.size <= MAX_COMMAND_DATA_LENGTH) { "APDU command data exceeds 65535 bytes" }
        require(expectedResponseDataLength == null || expectedResponseDataLength in 1..MAX_RESPONSE_DATA_LENGTH) {
            "APDU expected response data length must be in 1..65536"
        }
    }

    /** Whether another command APDU contributes data to the same chained command. */
    public val isChained: Boolean get() = cla.toInt() and COMMAND_CHAINING_BIT != 0

    /** Encodes this command using the shortest length form that represents its values. */
    public fun encode(): ByteArray {
        val payload = data.copy()
        val responseLength = expectedResponseDataLength
        val useExtended = payload.size > UByte.MAX_VALUE.toInt() || (responseLength ?: 0) > SHORT_MAXIMUM
        val result = ArrayList<Byte>(4 + payload.size + if (useExtended) 5 else 2)
        result += cla.toByte()
        result += instruction.toByte()
        result += parameter1.toByte()
        result += parameter2.toByte()
        if (payload.isNotEmpty()) {
            if (useExtended) {
                result += 0
                result += (payload.size ushr 8).toByte()
                result += payload.size.toByte()
            } else {
                result += payload.size.toByte()
            }
            payload.forEach(result::add)
        }
        responseLength?.let { length ->
            if (useExtended) {
                if (payload.isEmpty()) result += 0
                val encoded = if (length == MAX_RESPONSE_DATA_LENGTH) 0 else length
                result += (encoded ushr 8).toByte()
                result += encoded.toByte()
            } else {
                result += (if (length == SHORT_MAXIMUM) 0 else length).toByte()
            }
        }
        return result.toByteArray()
    }

    public companion object {
        public const val MAX_COMMAND_DATA_LENGTH: Int = 65_535
        public const val MAX_RESPONSE_DATA_LENGTH: Int = 65_536
        private const val SHORT_MAXIMUM: Int = 256
        private const val COMMAND_CHAINING_BIT: Int = 0x10

        /** Parses one complete command APDU and rejects ambiguous or trailing length forms. */
        public fun decode(encoded: ByteArray): NfcCommandApdu {
            require(encoded.size >= 4) { "Command APDU must contain a four-byte header" }
            val header = encoded.take(4).map(Byte::toUByte)
            if (encoded.size == 4) return NfcCommandApdu(header[0], header[1], header[2], header[3])

            val firstLength = encoded[4].toInt() and 0xff
            if (encoded.size == 5) {
                return NfcCommandApdu(
                    header[0], header[1], header[2], header[3],
                    expectedResponseDataLength = if (firstLength == 0) SHORT_MAXIMUM else firstLength,
                )
            }

            if (firstLength != 0) {
                val dataEnd = 5 + firstLength
                require(encoded.size == dataEnd || encoded.size == dataEnd + 1) {
                    "Short command APDU length does not match its data and Le fields"
                }
                val responseLength = encoded.getOrNull(dataEnd)?.let { value ->
                    (value.toInt() and 0xff).let { if (it == 0) SHORT_MAXIMUM else it }
                }
                return NfcCommandApdu(
                    header[0], header[1], header[2], header[3],
                    ImmutableBytes.of(encoded.copyOfRange(5, dataEnd)), responseLength,
                )
            }

            require(encoded.size >= 7) { "Extended command APDU is missing its two-byte length" }
            val extendedLength = unsignedShort(encoded, 5)
            if (encoded.size == 7) {
                return NfcCommandApdu(
                    header[0], header[1], header[2], header[3],
                    expectedResponseDataLength = if (extendedLength == 0) MAX_RESPONSE_DATA_LENGTH else extendedLength,
                )
            }
            require(extendedLength > 0) { "Extended command APDU Lc must be positive when data is present" }
            val dataEnd = 7 + extendedLength
            require(encoded.size == dataEnd || encoded.size == dataEnd + 2) {
                "Extended command APDU length does not match its data and Le fields"
            }
            val responseLength = if (encoded.size == dataEnd + 2) {
                unsignedShort(encoded, dataEnd).let { if (it == 0) MAX_RESPONSE_DATA_LENGTH else it }
            } else null
            return NfcCommandApdu(
                header[0], header[1], header[2], header[3],
                ImmutableBytes.of(encoded.copyOfRange(7, dataEnd)), responseLength,
            )
        }

        private fun unsignedShort(bytes: ByteArray, offset: Int): Int =
            ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)
    }
}

/** An immutable ISO/IEC 7816-4 response APDU. */
internal data class NfcResponseApdu(
    public val data: ImmutableBytes = ImmutableBytes.of(byteArrayOf()),
    public val statusWord: UShort,
) {
    public val statusByte1: UByte get() = (statusWord.toInt() ushr 8).toUByte()
    public val statusByte2: UByte get() = statusWord.toUByte()

    public fun encode(): ByteArray = data.copy() + byteArrayOf(statusByte1.toByte(), statusByte2.toByte())

    public companion object {
        public fun decode(encoded: ByteArray): NfcResponseApdu {
            require(encoded.size >= 2) { "Response APDU must contain a status word" }
            val status = (((encoded[encoded.lastIndex - 1].toInt() and 0xff) shl 8) or
                (encoded.last().toInt() and 0xff)).toUShort()
            return NfcResponseApdu(ImmutableBytes.of(encoded.copyOf(encoded.size - 2)), status)
        }
    }
}

/** Status words emitted by the common NFC state machines. */
internal object NfcStatusWord {
    public const val SUCCESS: UShort = 0x9000u
    public const val WRONG_LENGTH: UShort = 0x6700u
    public const val CONDITIONS_NOT_SATISFIED: UShort = 0x6985u
    public const val FILE_NOT_FOUND: UShort = 0x6a82u
    public const val WRONG_DATA: UShort = 0x6a80u
    public const val INCORRECT_PARAMETERS: UShort = 0x6a86u
    public const val INSTRUCTION_NOT_SUPPORTED: UShort = 0x6d00u
    public const val CLASS_NOT_SUPPORTED: UShort = 0x6e00u
    public const val UNKNOWN_ERROR: UShort = 0x6f00u

    /** Encodes `61xx`, where zero denotes 256 or more remaining bytes. */
    public fun moreData(remaining: Int): UShort {
        require(remaining > 0)
        return (0x6100 or if (remaining >= 256) 0 else remaining).toUShort()
    }
}
