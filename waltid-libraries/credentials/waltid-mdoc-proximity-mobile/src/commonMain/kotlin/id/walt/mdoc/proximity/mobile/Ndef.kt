package id.walt.mdoc.proximity.mobile

import id.walt.mdoc.proximity.ImmutableBytes

private val TYPED_NDEF_FORMATS = setOf(
    NdefTypeNameFormat.WELL_KNOWN,
    NdefTypeNameFormat.MIME_MEDIA,
    NdefTypeNameFormat.ABSOLUTE_URI,
    NdefTypeNameFormat.EXTERNAL,
)

/** NFC Forum NDEF Type Name Format. */
internal enum class NdefTypeNameFormat(val code: UByte) {
    EMPTY(0u),
    WELL_KNOWN(1u),
    MIME_MEDIA(2u),
    ABSOLUTE_URI(3u),
    EXTERNAL(4u),
    UNKNOWN(5u),
    UNCHANGED(6u),
    ;

    public companion object {
        internal fun fromCode(code: Int): NdefTypeNameFormat = entries.firstOrNull { it.code.toInt() == code }
            ?: throw IllegalArgumentException("Reserved NDEF TNF is not supported")
    }
}

/** One logical, fully reassembled NDEF record. */
internal data class NdefRecord(
    public val typeNameFormat: NdefTypeNameFormat,
    public val type: ImmutableBytes = ImmutableBytes.of(byteArrayOf()),
    public val identifier: ImmutableBytes = ImmutableBytes.of(byteArrayOf()),
    public val payload: ImmutableBytes = ImmutableBytes.of(byteArrayOf()),
) {
    init {
        require(typeNameFormat != NdefTypeNameFormat.UNCHANGED) {
            "UNCHANGED is valid only in encoded continuation chunks"
        }
        require(type.size <= UByte.MAX_VALUE.toInt()) { "NDEF type exceeds 255 bytes" }
        require(identifier.size <= UByte.MAX_VALUE.toInt()) { "NDEF identifier exceeds 255 bytes" }
        if (typeNameFormat == NdefTypeNameFormat.EMPTY) {
            require(type.size == 0 && identifier.size == 0 && payload.size == 0) {
                "An empty NDEF record cannot contain type, identifier, or payload bytes"
            }
        }
        if (typeNameFormat == NdefTypeNameFormat.UNKNOWN) {
            require(type.size == 0) { "An unknown NDEF record cannot contain a type" }
        }
        if (typeNameFormat in TYPED_NDEF_FORMATS) {
            require(type.size > 0) { "A typed NDEF record must contain a type" }
        }
    }
}

/** Peer-input limits applied before NDEF allocation or reassembly. */
internal data class NdefLimits(
    public val maximumMessageBytes: Int = 65_535,
    public val maximumRecordCount: Int = 64,
    public val maximumRecordPayloadBytes: Int = 65_535,
) {
    init {
        require(maximumMessageBytes > 0)
        require(maximumRecordCount > 0)
        require(maximumRecordPayloadBytes >= 0)
    }
}

/** A complete NDEF message with deterministic encoding and strict bounded decoding. */
internal data class NdefMessage(val records: List<NdefRecord>) {
    init {
        require(records.isNotEmpty()) { "An NDEF message must contain at least one record" }
    }

    public fun encode(limits: NdefLimits = NdefLimits()): ByteArray {
        require(records.size <= limits.maximumRecordCount) { "NDEF record count exceeds the configured limit" }
        val output = ByteAccumulator(limits.maximumMessageBytes)
        records.forEachIndexed { index, record ->
            require(record.payload.size <= limits.maximumRecordPayloadBytes) {
                "NDEF record payload exceeds the configured limit"
            }
            var flags = record.typeNameFormat.code.toInt()
            if (index == 0) flags = flags or MESSAGE_BEGIN
            if (index == records.lastIndex) flags = flags or MESSAGE_END
            val short = record.payload.size <= UByte.MAX_VALUE.toInt()
            if (short) flags = flags or SHORT_RECORD
            if (record.identifier.size > 0) flags = flags or IDENTIFIER_LENGTH_PRESENT
            output.add(flags)
            output.add(record.type.size)
            if (short) output.add(record.payload.size) else output.addUnsignedInt(record.payload.size)
            if (record.identifier.size > 0) output.add(record.identifier.size)
            output.add(record.type.copy())
            output.add(record.identifier.copy())
            output.add(record.payload.copy())
        }
        return output.toByteArray()
    }

    public companion object {
        private const val MESSAGE_BEGIN: Int = 0x80
        private const val MESSAGE_END: Int = 0x40
        private const val CHUNK_FOLLOWS: Int = 0x20
        private const val SHORT_RECORD: Int = 0x10
        private const val IDENTIFIER_LENGTH_PRESENT: Int = 0x08

        public fun decode(encoded: ByteArray, limits: NdefLimits = NdefLimits()): NdefMessage {
            require(encoded.isNotEmpty()) { "NDEF message is empty" }
            require(encoded.size <= limits.maximumMessageBytes) { "NDEF message exceeds the configured limit" }
            val cursor = ByteCursor(encoded)
            val records = mutableListOf<NdefRecord>()
            var physicalRecordCount = 0
            var chunk: Chunk? = null
            var ended = false
            while (!cursor.exhausted) {
                require(!ended) { "NDEF message contains trailing bytes after its ME record" }
                physicalRecordCount++
                require(physicalRecordCount <= limits.maximumRecordCount) {
                    "NDEF physical record count exceeds the configured limit"
                }
                val flags = cursor.readUnsignedByte()
                val begin = flags and MESSAGE_BEGIN != 0
                val end = flags and MESSAGE_END != 0
                val follows = flags and CHUNK_FOLLOWS != 0
                val short = flags and SHORT_RECORD != 0
                val hasIdentifierLength = flags and IDENTIFIER_LENGTH_PRESENT != 0
                val tnf = NdefTypeNameFormat.fromCode(flags and 0x07)
                if (physicalRecordCount == 1) require(begin) { "First NDEF record must set MB" }
                else require(!begin) { "Only the first NDEF record may set MB" }
                require(!(end && follows)) { "An NDEF chunk cannot set both CF and ME" }

                val typeLength = cursor.readUnsignedByte()
                val payloadLength = if (short) cursor.readUnsignedByte() else cursor.readBoundedUnsignedInt(
                    limits.maximumRecordPayloadBytes,
                    "NDEF record payload",
                )
                val identifierLength = if (hasIdentifierLength) cursor.readUnsignedByte() else 0
                require(payloadLength <= limits.maximumRecordPayloadBytes) {
                    "NDEF record payload exceeds the configured limit"
                }
                val type = cursor.read(typeLength)
                val identifier = cursor.read(identifierLength)
                val payload = cursor.read(payloadLength)

                if (chunk == null) {
                    require(tnf != NdefTypeNameFormat.UNCHANGED) {
                        "NDEF continuation appears without an initial chunk"
                    }
                    validateEncodedRecord(tnf, type, identifier, payload, short, hasIdentifierLength, follows)
                    if (follows) {
                        chunk = Chunk(tnf, type, identifier, ByteAccumulator(limits.maximumRecordPayloadBytes).apply {
                            add(payload)
                        })
                    } else {
                        records += NdefRecord(
                            tnf,
                            ImmutableBytes.of(type),
                            ImmutableBytes.of(identifier),
                            ImmutableBytes.of(payload),
                        )
                    }
                } else {
                    require(tnf == NdefTypeNameFormat.UNCHANGED) { "NDEF continuation TNF must be UNCHANGED" }
                    require(type.isEmpty() && identifier.isEmpty() && !hasIdentifierLength) {
                        "NDEF continuation cannot contain a type or identifier"
                    }
                    chunk.payload.add(payload)
                    if (!follows) {
                        records += NdefRecord(
                            chunk.typeNameFormat,
                            ImmutableBytes.of(chunk.type),
                            ImmutableBytes.of(chunk.identifier),
                            ImmutableBytes.of(chunk.payload.toByteArray()),
                        )
                        chunk = null
                    }
                }
                require(records.size <= limits.maximumRecordCount) { "NDEF record count exceeds the configured limit" }
                if (end) {
                    require(chunk == null) { "NDEF message ended inside a chunked record" }
                    ended = true
                }
            }
            require(ended) { "Last NDEF record must set ME" }
            return NdefMessage(records.toList())
        }

        private fun validateEncodedRecord(
            tnf: NdefTypeNameFormat,
            type: ByteArray,
            identifier: ByteArray,
            payload: ByteArray,
            short: Boolean,
            hasIdentifierLength: Boolean,
            follows: Boolean,
        ) {
            if (tnf == NdefTypeNameFormat.EMPTY) {
                require(
                    type.isEmpty() && identifier.isEmpty() && payload.isEmpty() && short &&
                        !hasIdentifierLength && !follows
                ) {
                    "An empty NDEF record must use the unchunked short form without type, ID, or payload"
                }
            }
            if (tnf == NdefTypeNameFormat.UNKNOWN) {
                require(type.isEmpty()) { "An unknown NDEF record cannot contain a type" }
            }
            if (tnf in TYPED_NDEF_FORMATS) {
                require(type.isNotEmpty()) { "A typed NDEF record must contain a type" }
            }
        }

        private data class Chunk(
            val typeNameFormat: NdefTypeNameFormat,
            val type: ByteArray,
            val identifier: ByteArray,
            val payload: ByteAccumulator,
        )
    }
}

private class ByteCursor(private val bytes: ByteArray) {
    private var offset: Int = 0
    val exhausted: Boolean get() = offset == bytes.size

    fun readUnsignedByte(): Int {
        require(offset < bytes.size) { "Truncated NDEF message" }
        return bytes[offset++].toInt() and 0xff
    }

    fun readBoundedUnsignedInt(maximum: Int, field: String): Int {
        var value = 0uL
        repeat(4) { value = (value shl 8) or readUnsignedByte().toULong() }
        require(value <= maximum.toULong()) { "$field exceeds the configured limit" }
        return value.toInt()
    }

    fun read(length: Int): ByteArray {
        require(length >= 0 && length <= bytes.size - offset) { "Truncated NDEF message" }
        return bytes.copyOfRange(offset, offset + length).also { offset += length }
    }
}

private class ByteAccumulator(private val maximumSize: Int) {
    private val chunks: MutableList<ByteArray> = mutableListOf()
    private var currentSize: Int = 0

    fun add(value: Int) {
        require(value in 0..255)
        add(byteArrayOf(value.toByte()))
    }

    fun addUnsignedInt(value: Int) {
        require(value >= 0)
        add(byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte()))
    }

    fun add(bytes: ByteArray) {
        require(bytes.size <= maximumSize - currentSize) { "Encoded NFC data exceeds the configured limit" }
        if (bytes.isNotEmpty()) chunks += bytes.copyOf()
        currentSize += bytes.size
    }

    fun toByteArray(): ByteArray {
        val output = ByteArray(currentSize)
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(output, offset)
            offset += chunk.size
        }
        return output
    }
}
